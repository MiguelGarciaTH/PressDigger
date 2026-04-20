package arquivo.processor;

import arquivo.model.Article;
import arquivo.model.ArticleChunkMedium;
import arquivo.repository.ArticleChunkMediumRepository;
import arquivo.repository.ArticleRepository;
import arquivo.services.CohereEmbeddingClient;
import arquivo.services.EmbeddingClient;
import arquivo.services.MetricService;
import arquivo.services.OpenAiEmbeddingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.text.BreakIterator;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(name = "scribe-ref.arquivo.scribe-news-embeddings-processor-backfill.enable", havingValue = "true")
public class TextEmbeddingBackfill {

    private static final Logger LOG = LoggerFactory.getLogger(TextEmbeddingBackfill.class);
    public static final int SHOW_STATS_INTERVAL_MINS = 1;

    private final MetricService metricService;

    private long fetchedArticlesWithoutMediumChunksTotal, savedArticlesWithMediumChunksTotal, savedMediumChunksTotal;
    private final LocalDateTime start = LocalDateTime.now(ZoneOffset.UTC);
    private LocalDateTime nextProgressLog = start.plusMinutes(SHOW_STATS_INTERVAL_MINS);
    private final EmbeddingClient embeddingClient;
    private final boolean useCohere;

    private final ArticleRepository articleRepository;
    private final ArticleChunkMediumRepository articleChunkRepository;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public TextEmbeddingBackfill(MetricService metricService,
                                 ArticleRepository articleRepository,
                                 ArticleChunkMediumRepository articleChunkRepository,
                                 TransactionTemplate transactionTemplate,
                                 @Value("${scribe-ref.arquivo.scribe-news-embeddings-processor.open-ai.api-key}") String openAiApiKey,
                                 @Value("${scribe-ref.arquivo.scribe-news-embeddings-processor.cohere.api-key:}") String cohereApiKey,
                                 @Value("${scribe-ref.embedding.provider:openai}") String embeddingProvider) {

        this.metricService = metricService;
        this.articleRepository = articleRepository;
        this.articleChunkRepository = articleChunkRepository;
        this.transactionTemplate = transactionTemplate;
        this.useCohere = "cohere".equalsIgnoreCase(embeddingProvider);
        if (this.useCohere) {
            this.embeddingClient = new CohereEmbeddingClient(cohereApiKey);
        } else {
            this.embeddingClient = new OpenAiEmbeddingClient(openAiApiKey);
        }

        fetchedArticlesWithoutMediumChunksTotal = metricService.loadValue("arquivo_embeddings_processor_fechted_items_without_medium_chunks_total");
        savedArticlesWithMediumChunksTotal = metricService.loadValue("arquivo_embeddings_processor_saved_items_with_medium_chunks_total");
        savedMediumChunksTotal = metricService.loadValue("arquivo_embeddings_processor_saved_medium_chunks_total");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void backfill() {
        LOG.info("Starting backfill process (provider={})...", useCohere ? "cohere" : "openai");

        // 1. Create chunks for articles that have none yet (only applies when using the primary provider, i.e. OpenAI)
        if (!useCohere) {
            final List<Article> articlesWithoutChunks = articleRepository.findAllWithoutChunks();
            LOG.info("Fetched {} articles without any chunks", articlesWithoutChunks.size());
            fetchedArticlesWithoutMediumChunksTotal += articlesWithoutChunks.size();

            int j = 0;
            for (Article article : articlesWithoutChunks) {
                final String title = article.getTitle() != null ? article.getTitle().trim() : "";
                final List<String> chunks = createChunksByThreeSentences(article.getSummary());
                int i = 0;
                for (String chunk : chunks) {
                    String normalizedChunk = chunk.trim().replaceAll("[.,;:!?]+$", "");
                    String textToEmbed = title.isBlank() ? normalizedChunk : title + "\n" + normalizedChunk;
                    float[] vector = embeddingClient.getDocumentEmbedding(textToEmbed);
                    articleChunkRepository.save(new ArticleChunkMedium(article, i++, chunk, vector));
                }
                LOG.info("Created {} chunks for article id {} ( {}/{} )", i, article.getId(), j++, articlesWithoutChunks.size());
            }
        }

        // 2. Backfill null embeddings on existing chunks for the active provider
        final List<Article> articlesWithNullEmbedding = transactionTemplate.execute(status -> useCohere
                ? articleRepository.findAllWithNullEmbeddingCohere()
                : articleRepository.findAllWithNullEmbedding());
        LOG.info("Fetched {} articles with null {} embedding on their chunks", articlesWithNullEmbedding.size(), useCohere ? "cohere" : "openai");

        int k = 0;
        for (Article article : articlesWithNullEmbedding) {
            final String title = article.getTitle() != null ? article.getTitle().trim() : "";

            // Collect chunks that need backfill and their texts
            List<ArticleChunkMedium> chunksToBackfill = new ArrayList<>();
            List<String> textsToEmbed = new ArrayList<>();
            for (ArticleChunkMedium chunk : article.getArticleChunksMedium()) {
                boolean needsBackfill = useCohere ? chunk.getEmbeddingCohere() == null : chunk.getEmbedding() == null;
                if (!needsBackfill) continue;
                chunksToBackfill.add(chunk);
                String normalizedContent = chunk.getContent().trim().replaceAll("[.,;:!?]+$", "");
                textsToEmbed.add(title.isBlank() ? normalizedContent : title + "\n" + normalizedContent);
            }

            if (textsToEmbed.isEmpty()) continue;

            // Batch embed all chunks for this article in one API call
            List<float[]> vectors = embeddingClient.getDocumentEmbeddingBatch(textsToEmbed);

            // Save in its own transaction so it commits immediately
            final List<ArticleChunkMedium> finalChunks = chunksToBackfill;
            transactionTemplate.executeWithoutResult(status -> {
                for (int idx = 0; idx < finalChunks.size(); idx++) {
                    String vectorLiteral = embeddingClient.toPgVectorLiteral(vectors.get(idx));
                    if (useCohere) {
                        articleChunkRepository.updateEmbeddingCohereById(finalChunks.get(idx).getId(), vectorLiteral);
                    } else {
                        articleChunkRepository.updateEmbeddingById(finalChunks.get(idx).getId(), vectorLiteral);
                    }
                }
            });
            if (++k % 100 == 0) {
                LOG.info("Backfilled {}/{} articles", k, articlesWithNullEmbedding.size());
            }
        }
        LOG.info("Backfilled {} articles with {} embeddings", k, useCohere ? "cohere" : "openai");

        metricService.updateValue("arquivo_embeddings_processor_saved_items_with_medium_chunks_total", 0);
        metricService.updateValue("arquivo_embeddings_processor_saved_medium_chunks_total", 0);

        printStats();

    }


    public List<String> createChunksByThreeSentences(String summary) {
        List<String> sentences = new ArrayList<>();
        BreakIterator iterator = BreakIterator.getSentenceInstance(new Locale("pt", "PT"));
        iterator.setText(summary);

        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String sentence = summary.substring(start, end).trim();
            if (!sentence.isEmpty()) {
                sentences.add(sentence);
            }
        }

        // Group into chunks of 3 sentences with 1 sentence overlap
        List<String> chunks = new ArrayList<>();
        int chunkSize = 3;
        int overlap = 1;

        for (int i = 0; i < sentences.size(); i += (chunkSize - overlap)) {
            int endIdx = Math.min(i + chunkSize, sentences.size());
            String chunk = String.join(" ", sentences.subList(i, endIdx));
            chunks.add(chunk);

            if (endIdx >= sentences.size()) break;
        }

        // Handle single sentences as standalone chunks if needed
        if (chunks.isEmpty() && !sentences.isEmpty()) {
            chunks.addAll(sentences);
        }

        return chunks;
    }


    private void printStats() {
        // just to show the progress every SHOW_STATS_INTERVAL_MINS minutes
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (now.isAfter(nextProgressLog)) {
            LOG.info("------------------------------------");
            LOG.info("Total fechted articles for backfill: {}", fetchedArticlesWithoutMediumChunksTotal);
            LOG.info("Total articles saved with chunks: {}", savedArticlesWithMediumChunksTotal);
            LOG.info("Total chunks saved: {}", savedMediumChunksTotal);
            LOG.info("Elapsed time: {} minutes", java.time.Duration.between(start, now).toMinutes());
            while (!now.isBefore(nextProgressLog)) {
                nextProgressLog = nextProgressLog.plusMinutes(SHOW_STATS_INTERVAL_MINS);
            }
        }
    }
}
