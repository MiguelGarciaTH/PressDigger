package arquivo.services;

/**
 * Abstraction for embedding providers (OpenAI, Cohere, etc.).
 */
public interface EmbeddingClient {

    /**
     * Get embedding for a document chunk (used when indexing/storing).
     */
    float[] getDocumentEmbedding(String text);

    /**
     * Get embedding for a search query (used at query time).
     * Some providers (e.g., Cohere) use different input types for queries vs documents.
     * For providers that don't distinguish, this defaults to the same as getDocumentEmbedding.
     */
    float[] getQueryEmbedding(String text);

    /**
     * Get embeddings for multiple texts in a single API call (batch).
     * Default implementation calls getDocumentEmbedding one by one.
     */
    default java.util.List<float[]> getDocumentEmbeddingBatch(java.util.List<String> texts) {
        java.util.List<float[]> results = new java.util.ArrayList<>(texts.size());
        for (String text : texts) {
            results.add(getDocumentEmbedding(text));
        }
        return results;
    }

    /**
     * Convert a float[] vector to a pgvector literal string, e.g. "[0.1,0.2,...]"
     */
    default String toPgVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
