package arquivo.services;

import arquivo.repository.RateLimiterRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.Http11SslContextSpec;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

public class WebClientService {

    private static final Logger LOG = LoggerFactory.getLogger(WebClientService.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MS = 2_000;

    private final WebClient webClient;
    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;

    public WebClientService(RateLimiterRepository rateLimiterRepository) {
        final HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30_000)        // 30s TCP connect (was 5_000_000ms!)
                .responseTimeout(Duration.ofSeconds(60))                      // 60s response (was 5000s!)
                .secure(sslSpec -> sslSpec
                        .sslContext(Http11SslContextSpec.forClient())         // default trust store (verifies certs)
                        .handshakeTimeout(Duration.ofSeconds(30)));           // 30s TLS handshake (was default 10s → caused the error)

        webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(codecs -> codecs
                        .defaultCodecs()
                        .maxInMemorySize(1500 * 1024))
                .build();

        this.objectMapper = new ObjectMapper();
        this.rateLimiterService = new RateLimiterService(rateLimiterRepository);
    }

    public JsonNode get(String url, String service) {
        long backoffMs = INITIAL_BACKOFF_MS;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                if (rateLimiterService != null) {
                    rateLimiterService.increment(service);
                }

                final JsonNode response = objectMapper.readTree(webClient.get()
                        .uri(url)
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve().bodyToMono(String.class).block());
                LOG.debug("Response for {} : {}", url, response);
                return response;

            } catch (JsonProcessingException e) {
                // Malformed JSON is a permanent error — no point retrying
                LOG.error("Failed to parse JSON response from {}", url, e);
                return null;

            } catch (WebClientResponseException e) {
                if (e.getStatusCode().is4xxClientError()) {
                    // 4xx = permanent (bad URL, auth) — don't waste retries
                    LOG.warn("Permanent HTTP {} from {}, not retrying", e.getStatusCode().value(), url);
                    return null;
                }
                LOG.warn("HTTP {} from {} (attempt {}/{})", e.getStatusCode().value(), url, attempt, MAX_ATTEMPTS);

            } catch (WebClientRequestException e) {
                // Covers SSL handshake timeout, connect timeout, read timeout, etc.
                LOG.warn("Request error for {} (attempt {}/{}): {}", url, attempt, MAX_ATTEMPTS, e.getMessage());
            }

            if (attempt < MAX_ATTEMPTS) {
                LOG.info("Waiting {}ms before retry {}/{} for {}", backoffMs, attempt + 1, MAX_ATTEMPTS, url);
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
                backoffMs *= 2; // exponential backoff: 2s → 4s → 8s
            }
        }

        LOG.error("Failed to fetch {} after {} attempts", url, MAX_ATTEMPTS);
        return null;
    }

    public JsonNode post(String url, JsonNode body) {
        long backoffMs = INITIAL_BACKOFF_MS;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                final JsonNode response = objectMapper.readTree(webClient.post()
                        .uri(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body)
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve().bodyToMono(String.class).block());
                LOG.debug("Response for {} : {}", url, response);
                return response;

            } catch (JsonProcessingException e) {
                LOG.error("Failed to parse JSON response from {}", url, e);
                return null;

            } catch (WebClientResponseException e) {
                if (e.getStatusCode().is4xxClientError()) {
                    LOG.warn("Permanent HTTP {} from {}, not retrying", e.getStatusCode().value(), url);
                    return null;
                }
                LOG.warn("HTTP {} from {} (attempt {}/{})", e.getStatusCode().value(), url, attempt, MAX_ATTEMPTS);

            } catch (WebClientRequestException e) {
                LOG.warn("Request error for {} (attempt {}/{}): {}", url, attempt, MAX_ATTEMPTS, e.getMessage());
            }

            if (attempt < MAX_ATTEMPTS) {
                LOG.info("Waiting {}ms before retry {}/{} for {}", backoffMs, attempt + 1, MAX_ATTEMPTS, url);
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
                backoffMs *= 2;
            }
        }

        LOG.error("Failed to POST to {} after {} attempts", url, MAX_ATTEMPTS);
        return null;
    }
}
