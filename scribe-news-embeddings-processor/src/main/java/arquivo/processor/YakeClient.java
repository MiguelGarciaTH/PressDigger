package arquivo.processor;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Calls the YAKE Docker service to extract keywords from Portuguese text.
 * Uses Java 11+ built-in HttpClient — no extra dependencies needed.
 */
public class YakeClient {

    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper mapper;

    public YakeClient(String baseUrl) {
        this.baseUrl = baseUrl; // e.g. "http://yake:8000" inside Docker, "http://localhost:8000" locally
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.mapper = new ObjectMapper();
    }

    public record Keyword(String keyword, double score) {}

    public List<Keyword> extract(String text) throws Exception {
        return extract(text, "pt", 10, 2);
    }

    public List<Keyword> extract(String text, String language, int maxKeywords, int maxNgramSize) throws Exception {
        // Validate input
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Text cannot be null or empty");
        }

        String jsonBody = mapper.writeValueAsString(Map.of(
                "text", text,
                "language", language,
                "max_keywords", maxKeywords,
                "max_ngram_size", maxNgramSize
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/extract"))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            throw new RuntimeException("YAKE service error: " + response.statusCode() + " " + response.body());
        }

        var result = mapper.readTree(response.body());
        return mapper.convertValue(
                result.get("keywords"),
                mapper.getTypeFactory().constructCollectionType(List.class, Keyword.class)
        );
    }
}