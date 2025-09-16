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
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

public class WebClientService {

    private static final Logger LOG = LoggerFactory.getLogger(WebClientService.class);

    private final WebClient webClient;
    private RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;
    private int retryCounter = 0;

    public WebClientService(RateLimiterRepository rateLimiterRepository) {
        this();
        this.rateLimiterService = new RateLimiterService(rateLimiterRepository);
    }

    public WebClientService() {
        final HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000 * 1000)
                .responseTimeout(Duration.ofSeconds(5000));

        webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(codecs -> codecs
                        .defaultCodecs()
                        .maxInMemorySize(1500 * 1024))
                .build();

        this.objectMapper = new ObjectMapper();
    }

    public JsonNode get(String url, String service) {
        if (retryCounter > 2) {
            retryCounter = 0;
            return null;
        }
        try {
            if(rateLimiterService != null) {
                rateLimiterService.increment(service);
            }

            final JsonNode response = objectMapper.readTree(webClient.get()
                    .uri(url)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve().bodyToMono(String.class).block());
            LOG.debug("Response for {} : {}", url, response);
            retryCounter = 0;
            return response;
        } catch (JsonProcessingException e) {
            LOG.error("Problem fetching {}", url);
        } catch (WebClientResponseException | WebClientRequestException e2) {
            LOG.error("Problem fetching {}: {}", url, e2.getMessage());
            try {
                Thread.sleep(500L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            retryCounter++;
            LOG.info("Will retry {}^nt attempt (max 2): {}", retryCounter, url);
            return get(url, service);
        }
        return null;
    }

    public JsonNode post(String url, JsonNode body) {
        if (retryCounter > 2) {
            retryCounter = 0;
            return null;
        }
        try {

            final JsonNode response = objectMapper.readTree(webClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve().bodyToMono(String.class).block());
            LOG.debug("Response for {} : {}", url, response);
            retryCounter = 0;
            return response;
        } catch (JsonProcessingException e) {
            LOG.error("Problem fetching {}", url);
        } catch (WebClientResponseException e2) {
            LOG.error("Problem fetching {}: {}", url, e2.getMessage());
            try {
                Thread.sleep(500L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            retryCounter++;
            LOG.info("Will retry {}^nt attempt (max 2): {}", retryCounter, url);
            return post(url, body);
        }
        return null;
    }
}
