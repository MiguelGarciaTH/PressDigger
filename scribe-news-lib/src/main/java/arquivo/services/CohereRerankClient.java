package arquivo.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Cohere Rerank client using rerank-multilingual-v3.0.
 * Re-scores a list of candidate documents against a query using a cross-encoder,
 * giving much better relevance ordering than pure vector distance.
 */
public class CohereRerankClient {

    private static final Logger LOG = LoggerFactory.getLogger(CohereRerankClient.class);
    private static final String MODEL = "rerank-multilingual-v3.0";
    private static final String BASE_URL = "https://api.cohere.com";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public CohereRerankClient(String apiKey) {
        this.webClient = WebClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Reranks the given documents against the query.
     * Returns results sorted by relevance score descending (best first).
     *
     * @param query     the user's search query
     * @param documents the candidate document texts to rerank
     * @param topN      how many top results to return (must be <= documents.size())
     * @return list of RerankResult sorted by relevance_score DESC
     */
    public List<RerankResult> rerank(String query, List<String> documents, int topN) {
        if (documents.isEmpty()) return List.of();

        int effectiveTopN = Math.min(topN, documents.size());

        Map<String, Object> body = Map.of(
                "model", MODEL,
                "query", query,
                "documents", documents,
                "top_n", effectiveTopN,
                "return_documents", false
        );

        LOG.info("[Rerank] Calling Cohere rerank: query='{}...', candidates={}, top_n={}",
                query.substring(0, Math.min(60, query.length())), documents.size(), effectiveTopN);

        try {
            String responseBody = webClient.post()
                    .uri("/v1/rerank")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(responseBody);

            if (!root.has("results")) {
                LOG.error("[Rerank] Unexpected Cohere response (no 'results'): {}", responseBody);
                throw new RuntimeException("Cohere rerank response missing 'results' field: " + responseBody);
            }

            JsonNode results = root.get("results");
            List<RerankResult> rerankResults = new ArrayList<>(results.size());
            for (JsonNode result : results) {
                rerankResults.add(new RerankResult(
                        result.get("index").asInt(),
                        result.get("relevance_score").asDouble()
                ));
            }

            // Log top-5 scores so we can confirm reranking is working
            String topScores = rerankResults.stream()
                    .limit(5)
                    .map(r -> String.format("[idx=%d score=%.4f]", r.index(), r.relevanceScore()))
                    .collect(Collectors.joining(", "));
            LOG.info("[Rerank] Top scores: {}", topScores);

            // API returns results already sorted by relevance_score DESC
            return rerankResults;
        } catch (Exception e) {
            LOG.error("[Rerank] Cohere rerank failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to rerank with Cohere", e);
        }
    }

    public record RerankResult(int index, double relevanceScore) {
    }
}
