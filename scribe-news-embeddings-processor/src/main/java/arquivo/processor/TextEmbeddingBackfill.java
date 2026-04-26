package arquivo.processor;

import arquivo.model.Article;
import arquivo.repository.ArticleRepository;
import arquivo.services.CohereEmbeddingClient;
import arquivo.services.EmbeddingClient;
import arquivo.services.OpenAiEmbeddingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(name = "scribe-ref.arquivo.scribe-news-embeddings-processor-backfill.enable", havingValue = "true")
public class TextEmbeddingBackfill {

    private static final Logger LOG = LoggerFactory.getLogger(TextEmbeddingBackfill.class);
    public static final int SHOW_STATS_INTERVAL_MINS = 1;

    private final LocalDateTime start = LocalDateTime.now(ZoneOffset.UTC);
    private LocalDateTime nextProgressLog = start.plusMinutes(SHOW_STATS_INTERVAL_MINS);
    private final EmbeddingClient embeddingClient;
    private final boolean useCohere;

    private final ArticleRepository articleRepository;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public TextEmbeddingBackfill(ArticleRepository articleRepository,
                                 TransactionTemplate transactionTemplate,
                                 @Value("${scribe-ref.arquivo.scribe-news-embeddings-processor.open-ai.api-key}") String openAiApiKey,
                                 @Value("${scribe-ref.arquivo.scribe-news-embeddings-processor.cohere.api-key:}") String cohereApiKey,
                                 @Value("${scribe-ref.embedding.provider:openai}") String embeddingProvider) {

        this.articleRepository = articleRepository;
        this.transactionTemplate = transactionTemplate;
        this.useCohere = "cohere".equalsIgnoreCase(embeddingProvider);
        if (this.useCohere) {
            this.embeddingClient = new CohereEmbeddingClient(cohereApiKey);
        } else {
            this.embeddingClient = new OpenAiEmbeddingClient(openAiApiKey);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void backfill() {
        LOG.info("Starting article-level embedding backfill (provider={})...", useCohere ? "cohere" : "openai");

        final List<Article> articlesWithNullEmbedding = useCohere
                ? articleRepository.findAllWithNullArticleEmbeddingCohere()
                : articleRepository.findAllWithNullArticleEmbedding();
        LOG.info("Fetched {} articles with null {} article-level embedding",
                articlesWithNullEmbedding.size(), useCohere ? "cohere" : "openai");

        final int BATCH_SIZE = 96;
        int articleEmbedCount = 0;

        for (int batchStart = 0; batchStart < articlesWithNullEmbedding.size(); batchStart += BATCH_SIZE) {
            int batchEnd = Math.min(batchStart + BATCH_SIZE, articlesWithNullEmbedding.size());
            List<Article> batch = articlesWithNullEmbedding.subList(batchStart, batchEnd);

            List<String> textsToEmbed = new ArrayList<>(batch.size());
            for (Article article : batch) {
                String title = article.getTitle() != null ? article.getTitle().trim() : "";
                String summary = article.getSummary() != null ? article.getSummary().trim() : "";
                String normalizedSummary = summary.replaceAll("[.,;:!?]+$", "");
                textsToEmbed.add(title.isBlank() ? normalizedSummary : title + "\n" + normalizedSummary);
            }

            List<float[]> vectors = embeddingClient.getDocumentEmbeddingBatch(textsToEmbed);

            final List<Article> finalBatch = batch;
            transactionTemplate.executeWithoutResult(status -> {
                for (int idx = 0; idx < finalBatch.size(); idx++) {
                    String vectorLiteral = embeddingClient.toPgVectorLiteral(vectors.get(idx));
                    if (useCohere) {
                        articleRepository.updateEmbeddingCohereById(finalBatch.get(idx).getId(), vectorLiteral);
                    } else {
                        articleRepository.updateEmbeddingById(finalBatch.get(idx).getId(), vectorLiteral);
                    }
                }
            });

            articleEmbedCount += batch.size();
            LOG.info("Article-level embedding backfill: {}/{}", articleEmbedCount, articlesWithNullEmbedding.size());
            printStats();
        }

        LOG.info("Finished article-level embedding backfill: {} articles with {} embeddings",
                articleEmbedCount, useCohere ? "cohere" : "openai");
    }

    private void printStats() {
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (now.isAfter(nextProgressLog)) {
            LOG.info("------------------------------------");
            LOG.info("Elapsed time: {} minutes", java.time.Duration.between(start, now).toMinutes());
            while (!now.isBefore(nextProgressLog)) {
                nextProgressLog = nextProgressLog.plusMinutes(SHOW_STATS_INTERVAL_MINS);
            }
        }
    }
}
