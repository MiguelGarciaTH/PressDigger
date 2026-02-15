package arquivo.service;

import arquivo.exceptions.ResourceNotFoundException;
import arquivo.model.Article;
import arquivo.model.ArticleChunk;
import arquivo.repository.ArticleChunkRepository;
import arquivo.repository.ArticleRepository;
import arquivo.services.TextEmbeddingClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final ArticleChunkRepository articleChunkRepository;
    private final TextEmbeddingClient textEmbeddingClient;

    public ArticleService(Environment environment,
                          ArticleRepository articleRepository,
                          ArticleChunkRepository articleChunkRepository) {

        this.articleRepository = articleRepository;
        this.articleChunkRepository = articleChunkRepository;
        final String url = environment.getProperty("scribe-ref.arquivo.scribe-rest.embedding-service-url");
        this.textEmbeddingClient = new TextEmbeddingClient(url, new ObjectMapper(), true);
    }

    @Transactional(readOnly = true)
    public Article getArticle(int articleId) {
        Article article = articleRepository.findById(articleId).orElse(null);
        if (article == null) {
            throw new ResourceNotFoundException("article not found with id: " + articleId);
        }
        return article;
    }

    @Transactional(readOnly = true)
    public Page<Article> search(String inputText, Pageable pageable) {
        JsonNode embedded = textEmbeddingClient
                .getEmbeddings(inputText)
                .get("embedding");

        float[] queryEmbedding = textEmbeddingClient.toFloatArray(embedded);

        String pgVector = textEmbeddingClient.toPgVectorLiteral(queryEmbedding);

        return articleChunkRepository.searchByText(pgVector, inputText, pageable);
    }
}
