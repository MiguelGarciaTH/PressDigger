package arquivo.service;

import arquivo.exceptions.ResourceNotFoundException;
import arquivo.model.Article;
import arquivo.repository.ArticleChunkMediumRepository;
import arquivo.repository.ArticleRepository;
import arquivo.services.TextEmbeddingClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final ArticleChunkMediumRepository articleChunkMediumRepository;
    private final TextEmbeddingClient textEmbeddingClient;

    public ArticleService(Environment environment,
                          ArticleRepository articleRepository,
                          ArticleChunkMediumRepository articleChunkMediumRepository) {

        this.articleRepository = articleRepository;
        this.articleChunkMediumRepository = articleChunkMediumRepository;
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
    public Page<Article> search(String inputText, List<Integer> siteIds, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        // Normalize input text: trim, remove trailing punctuation
        String normalizedText = inputText.trim().replaceAll("[.,;:!?]+$", "");

        JsonNode embedded = textEmbeddingClient
                .getEmbeddings(normalizedText)
                .get("embedding");

        float[] queryEmbedding = textEmbeddingClient.toFloatArray(embedded);

        String pgVector = textEmbeddingClient.toPgVectorLiteral(queryEmbedding);

        // Use original input text for full-text search (it handles punctuation well)
        return articleChunkMediumRepository.searchByText(siteIds, startDate, endDate, pgVector, inputText, pageable);
    }
}
