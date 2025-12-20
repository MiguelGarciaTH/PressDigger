package arquivo.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.channel.ChannelOption;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

public class TextEmbeddingClient {

    private final ObjectMapper objectMapper;
    private final String url;
    private final WebClient webClient;
    private final String type;

    public TextEmbeddingClient(String url, ObjectMapper objectMapper, boolean isQueryTypeClient) {
        this.objectMapper = objectMapper;
        this.url = url;

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

        if (isQueryTypeClient) {
            type = "query";
        } else {
            type = "passage";
        }
    }

    public JsonNode getEmbeddings(String text) {
        final JsonNode body = prepareBody(text);
        try {
            return objectMapper.readTree(webClient.post()
                    .uri(url)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(body.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block());
        } catch (Exception e) {
            return null;
        }
    }

    private JsonNode prepareBody(String text) {
        final ObjectNode body = objectMapper.createObjectNode();
        body.put("text", text);
        body.put("kind", type);
        return body;
    }
}
