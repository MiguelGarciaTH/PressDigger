package arquivo.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cohere embedding client using embed-multilingual-v3.0 (1024 dimensions).
 * Supports search-type-aware embeddings via input_type parameter:
 * - "search_document" for indexing/storing chunks
 * - "search_query" for user queries at search time
 */
public class CohereEmbeddingClient implements EmbeddingClient {

    private static final Logger LOG = LoggerFactory.getLogger(CohereEmbeddingClient.class);
    private static final String MODEL = "embed-multilingual-v3.0";
    private static final String BASE_URL = "https://api.cohere.com";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public CohereEmbeddingClient(String apiKey) {
        // 96 embeddings × 1024 dims × ~7 chars/float in JSON ≈ 5–6 MB; set 16MB to be safe
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(ClientCodecConfigurer::defaultCodecs)
                .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
        this.webClient = WebClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .exchangeStrategies(strategies)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public float[] getDocumentEmbedding(String text) {
        return embed(text, "search_document");
    }

    @Override
    public float[] getQueryEmbedding(String text) {
        return embed(text, "search_query");
    }

    private static final int MAX_RETRIES = 5;
    private static final long INITIAL_BACKOFF_MS = 12_000; // 12s — Cohere trial: 5 req/min
    private static final int BATCH_SIZE = 96; // Cohere max texts per request

    @Override
    public List<float[]> getDocumentEmbeddingBatch(List<String> texts) {
        List<float[]> allResults = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += BATCH_SIZE) {
            List<String> batch = texts.subList(start, Math.min(start + BATCH_SIZE, texts.size()));
            allResults.addAll(embedBatch(batch, "search_document"));
        }
        return allResults;
    }

    private List<float[]> embedBatch(List<String> texts, String inputType) {
        Map<String, Object> body = Map.of(
                "model", MODEL,
                "texts", texts,
                "input_type", inputType,
                "embedding_types", List.of("float")
        );

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                String responseBody = webClient.post()
                        .uri("/v1/embed")
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                JsonNode root = objectMapper.readTree(responseBody);
                JsonNode embeddingsArray = root.get("embeddings").get("float");

                List<float[]> results = new ArrayList<>(embeddingsArray.size());
                for (int i = 0; i < embeddingsArray.size(); i++) {
                    JsonNode embeddingArray = embeddingsArray.get(i);
                    float[] result = new float[embeddingArray.size()];
                    for (int j = 0; j < embeddingArray.size(); j++) {
                        result[j] = embeddingArray.get(j).floatValue();
                    }
                    results.add(result);
                }
                return results;
            } catch (Exception e) {
                boolean isRateLimit = e.getMessage() != null && e.getMessage().contains("429");
                if (isRateLimit && attempt < MAX_RETRIES) {
                    long sleepMs = INITIAL_BACKOFF_MS * (1L << attempt);
                    LOG.warn("Cohere rate limit hit on batch (attempt {}/{}), backing off {}s", attempt + 1, MAX_RETRIES, sleepMs / 1000);
                    try {
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during backoff", ie);
                    }
                } else {
                    LOG.error("Error getting batch embedding from Cohere: {}", e.getMessage());
                    throw new RuntimeException("Failed to get batch embedding from Cohere", e);
                }
            }
        }
        throw new RuntimeException("Failed to get batch embedding from Cohere after " + MAX_RETRIES + " retries");
    }

    private float[] embed(String text, String inputType) {
        Map<String, Object> body = Map.of(
                "model", MODEL,
                "texts", List.of(text),
                "input_type", inputType,
                "embedding_types", List.of("float")
        );

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                String responseBody = webClient.post()
                        .uri("/v1/embed")
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                JsonNode root = objectMapper.readTree(responseBody);
                JsonNode embeddingArray = root.get("embeddings").get("float").get(0);

                float[] result = new float[embeddingArray.size()];
                for (int i = 0; i < embeddingArray.size(); i++) {
                    result[i] = embeddingArray.get(i).floatValue();
                }
                return result;
            } catch (Exception e) {
                boolean isRateLimit = e.getMessage() != null && e.getMessage().contains("429");
                if (isRateLimit && attempt < MAX_RETRIES) {
                    long sleepMs = INITIAL_BACKOFF_MS * (1L << attempt); // exponential: 12s, 24s, 48s, 96s, 192s
                    LOG.warn("Cohere rate limit hit (attempt {}/{}), backing off {}s", attempt + 1, MAX_RETRIES, sleepMs / 1000);
                    try {
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during backoff", ie);
                    }
                } else {
                    LOG.error("Error getting embedding from Cohere: {}", e.getMessage());
                    throw new RuntimeException("Failed to get embedding from Cohere", e);
                }
            }
        }
        throw new RuntimeException("Failed to get embedding from Cohere after " + MAX_RETRIES + " retries");
    }
}

