package arquivo.services;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.embeddings.EmbeddingCreateParams;
import com.openai.models.embeddings.EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class OpenAiEmbeddingClient {

    private static final Logger LOG = LoggerFactory.getLogger(OpenAiEmbeddingClient.class);
    private static final int DIMENSIONS = 2000; // max for pgvector HNSW index

    private final OpenAIClient client;

    public OpenAiEmbeddingClient(String apiKey) {
        this.client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
    }

    public float[] getEmbedding(String text) {
        try {
            var response = client.embeddings().create(
                    EmbeddingCreateParams.builder()
                            .model(EmbeddingModel.TEXT_EMBEDDING_3_LARGE)
                            .input(EmbeddingCreateParams.Input.ofString(text))
                            .dimensions(DIMENSIONS)
                            .build()
            );
            List<Float> values = response.data().get(0).embedding();
            float[] result = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                result[i] = values.get(i).floatValue();
            }
            return result;
        } catch (Exception e) {
            LOG.error("Error getting embedding from OpenAI: {}", e.getMessage());
            throw new RuntimeException("Failed to get embedding", e);
        }
    }

    public String toPgVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
