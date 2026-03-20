package arquivo.processor;

import arquivo.model.Article;
import arquivo.model.ArticleChunkMedium;
import arquivo.repository.ArticleChunkMediumRepository;
import arquivo.repository.ArticleRepository;
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

@Component
@ConditionalOnProperty(name = "scribe-ref.arquivo.scribe-news-embeddings-processor-backfill.enable", havingValue = "true")
public class TextEmbeddingBackfill {

    private static final Logger LOG = LoggerFactory.getLogger(TextEmbeddingBackfill.class);
    public static final int SHOW_STATS_INTERVAL_MINS = 1;

    private final MetricService metricService;

    private long fetchedArticlesWithoutMediumChunksTotal, savedArticlesWithMediumChunksTotal, savedMediumChunksTotal;
    private final LocalDateTime start = LocalDateTime.now(ZoneOffset.UTC);
    private LocalDateTime nextProgressLog = start.plusMinutes(SHOW_STATS_INTERVAL_MINS);
    private final OpenAiEmbeddingClient embeddingClient; // replaces TextEmbeddingClient

    private final ArticleRepository articleRepository;
    private final ArticleChunkMediumRepository articleChunkRepository;

    @Autowired
    public TextEmbeddingBackfill(MetricService metricService,
                                 ArticleRepository articleRepository,
                                 ArticleChunkMediumRepository articleChunkRepository,
                                 @Value("${scribe-ref.arquivo.scribe-news-embeddings-processor.open-ai.api-key}") String apiKey) {

        this.metricService = metricService;
        this.articleRepository = articleRepository;
        this.articleChunkRepository = articleChunkRepository;
        this.embeddingClient = new OpenAiEmbeddingClient(apiKey); // same key, new use

        fetchedArticlesWithoutMediumChunksTotal = metricService.loadValue("arquivo_embeddings_processor_fechted_items_without_medium_chunks_total");
        savedArticlesWithMediumChunksTotal = metricService.loadValue("arquivo_embeddings_processor_saved_items_with_medium_chunks_total");
        savedMediumChunksTotal = metricService.loadValue("arquivo_embeddings_processor_saved_medium_chunks_total");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void backfill() {
        LOG.info("Starting backfill process for articles chunks...");
        final List<Article> articles = articleRepository.findAllWithouChunks();

        LOG.info("Fetched {} articles without medium chunks", articles.size());
        fetchedArticlesWithoutMediumChunksTotal = fetchedArticlesWithoutMediumChunksTotal + articles.size();

        int j = 0;
        for (Article article : articles) {
            final List<String> chunks = createChunksByThreeSentences(article.getSummary());
            int i = 0;
            for (String chunk : chunks) {
                String normalizedText = chunk.trim().replaceAll("[.,;:!?]+$", "");
                float[] vector = embeddingClient.getEmbedding(normalizedText);
                articleChunkRepository.save(new ArticleChunkMedium(article, i++, chunk, vector));
            }
            LOG.info("Saved {} chunks for article id {} ( {}/{} )", i, article.getId(), j++, articles.size());
        }

        metricService.updateValue("arquivo_embeddings_processor_fechted_items_without_medium_chunks_total", articles.size());
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
