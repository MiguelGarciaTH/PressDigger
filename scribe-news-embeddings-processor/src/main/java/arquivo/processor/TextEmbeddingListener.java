package arquivo.processor;

import arquivo.model.*;
import arquivo.repository.*;
import arquivo.services.MetricService;
import arquivo.services.OpenAiEmbeddingClient;
import arquivo.utils.UrlNormalizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.text.BreakIterator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

@Component
@ConditionalOnProperty(name = "scribe-ref.arquivo.scribe-news-embeddings-processor.enable", havingValue = "true")
public class TextEmbeddingListener {

    private static final Logger LOG = LoggerFactory.getLogger(TextEmbeddingListener.class);
    public static final int SHOW_STATS_INTERVAL_MINS = 1;
    public static final String ARQUIVO_EMBEDDINGS_PROCESSOR_RESPONSE_ITEMS_INCOMPLETE_TOTAL = "arquivo_embeddings_processor_response_items_incomplete_total";
    public static final String ARQUIVO_EMBEDDINGS_PROCESSOR_RESPONSE_ITEMS_RECEIVED_TOTAL = "arquivo_embeddings_processor_response_items_received_total";
    public static final String ARQUIVO_EMBEDDINGS_PROCESSOR_RESPONSE_ITEMS_STORED_TOTAL = "arquivo_embeddings_processor_response_items_stored_total";

    private final ObjectMapper objectMapper;

    private final MetricService metricService;

    private final AtomicLong responseItemsIncompleteTotal = new AtomicLong(0);
    private final AtomicLong responseItemsReceivedTotal = new AtomicLong(0);
    private final AtomicLong responseItemsStoredTotal = new AtomicLong(0);

    private final LocalDateTime start = LocalDateTime.now(ZoneOffset.UTC);
    private LocalDateTime nextProgressLog = start.plusMinutes(SHOW_STATS_INTERVAL_MINS);
    private final OpenAiEmbeddingClient embeddingClient;
    private final YakeClient yakeClient;

    private final ArticleRepository articleRepository;
    private final ArticleChunkMediumRepository articleChunkMediumRepository;
    private final SiteRepository siteRepository;
    private final KeywordRepository keywordRepository;
    private final ArticleKeywordScoreRepository articleKeywordScoreRepository;
    private final AuthorRepository authorRepository;

    @Autowired
    public TextEmbeddingListener(MetricService metricService,
                                 ArticleRepository articleRepository,
                                 ArticleChunkMediumRepository articleChunkMediumRepository,
                                 SiteRepository siteRepository,
                                 KeywordRepository keywordRepository,
                                 ArticleKeywordScoreRepository articleKeywordScoreRepository,
                                 AuthorRepository authorRepository,
                                 @Value("${scribe-ref.arquivo.scribe-news-embeddings-processor.open-ai.api-key}") String apiKey) {
        this.metricService = metricService;
        this.objectMapper = new ObjectMapper();
        this.articleRepository = articleRepository;
        this.articleChunkMediumRepository = articleChunkMediumRepository;
        this.siteRepository = siteRepository;
        this.keywordRepository = keywordRepository;
        this.articleKeywordScoreRepository = articleKeywordScoreRepository;
        this.authorRepository = authorRepository;
        this.embeddingClient = new OpenAiEmbeddingClient(apiKey);
        this.yakeClient = new YakeClient("http://localhost:8002");

        responseItemsIncompleteTotal.set(metricService.loadValue(ARQUIVO_EMBEDDINGS_PROCESSOR_RESPONSE_ITEMS_INCOMPLETE_TOTAL));
        responseItemsReceivedTotal.set(metricService.loadValue(ARQUIVO_EMBEDDINGS_PROCESSOR_RESPONSE_ITEMS_RECEIVED_TOTAL));
        responseItemsStoredTotal.set(metricService.loadValue(ARQUIVO_EMBEDDINGS_PROCESSOR_RESPONSE_ITEMS_STORED_TOTAL));
    }

    @KafkaListener(
            topics = {"${scribe-ref.arquivo.scribe-news-embeddings-processor.kafka.to-listen.topic}"},
            containerFactory = "kafkaListenerContainerFactory",
            concurrency = "${scribe-ref.arquivo.scribe-news-embeddings-processor.kafka.to-listen.concurrency}")
    public void listener(ConsumerRecord<String, String> record, Acknowledgment ack, @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {
        LOG.trace("Received on topic {} on partition {} record {}", record.topic(), partition, record.value());

        metricService.updateValue(ARQUIVO_EMBEDDINGS_PROCESSOR_RESPONSE_ITEMS_RECEIVED_TOTAL, responseItemsReceivedTotal.incrementAndGet());

        try {
            String payload = record.value();
            if (payload == null || payload.isBlank()) {
                LOG.warn("Empty payload for key {}", record.key());
                metricService.updateValue(ARQUIVO_EMBEDDINGS_PROCESSOR_RESPONSE_ITEMS_INCOMPLETE_TOTAL, responseItemsIncompleteTotal.incrementAndGet());
                return;
            }

            final JsonNode responseItem = objectMapper.readTree(payload);

            final Site site = siteRepository.findById(responseItem.get("siteId").asInt()).orElse(null);
            if (site == null) {
                LOG.warn("Site with id {} not found, skipping article {}", responseItem.get("siteId").asInt(), responseItem.get("title").asText());
                metricService.updateValue(ARQUIVO_EMBEDDINGS_PROCESSOR_RESPONSE_ITEMS_INCOMPLETE_TOTAL, responseItemsIncompleteTotal.incrementAndGet());
                return;
            }

            final String summary = responseItem.get("summary").asText();

            Author author = null;
            if (responseItem.has("author") && !responseItem.get("author").isNull()) {
                author = createOrGetAuthor(responseItem.get("author").asText());
            }

            final Article article = articleRepository.save(
                    new Article(
                            responseItem.get("articleHash").asInt(),
                            site,
                            author,
                            responseItem.get("title").asText(),
                            summary,
                            parsePublishedDate(responseItem.get("publishedDate")),
                            responseItem.get("publishedDateConfidence").asDouble(),
                            responseItem.get("linkToArchive").asText(),
                            UrlNormalizer.normalize(responseItem.get("linkToArchive").asText()),
                            responseItem.get("linkToScreenshot").asText(),
                            responseItem.get("originalImagePath").asText(),
                            responseItem.get("smallImagePath").asText()
                    )
            );

            metricService.updateValue(ARQUIVO_EMBEDDINGS_PROCESSOR_RESPONSE_ITEMS_STORED_TOTAL, responseItemsStoredTotal.incrementAndGet());
            LOG.trace("Stored article {} with id {}", article.getTitle(), article.getId());

            // create keywords for the article using YAKE
            final List<YakeClient.Keyword> extractedKeywords = yakeClient.extract(summary, "pt", 5, 4);
            if (!extractedKeywords.isEmpty()) {
                final List<ArticleKeywordScore> articleKeywordScores = new ArrayList<>(extractedKeywords.size());
                for (YakeClient.Keyword extractedKeyword : extractedKeywords) {
                    Keyword keyword = keywordRepository.findByName(extractedKeyword.keyword()).orElse(null);
                    if (keyword == null) {
                        keyword = keywordRepository.save(new Keyword(extractedKeyword.keyword()));
                    }
                    articleKeywordScores.add(new ArticleKeywordScore(article, keyword, extractedKeyword.score()));
                }
                articleKeywordScoreRepository.saveAll(articleKeywordScores);
            }

            // Create both chunk lists first
            final String title = article.getTitle() != null ? article.getTitle().trim() : "";
            final List<String> chunksMedium = createChunksByThreeSentences(summary);
            int i = 0;
            for (String chunk : chunksMedium) {
                String normalizedChunk = chunk.trim().replaceAll("[.,;:!?]+$", "");
                // Prepend the article title so each chunk carries topic context for the embedding model
                String textToEmbed = title.isBlank() ? normalizedChunk : title + "\n" + normalizedChunk;
                float[] vector = embeddingClient.getEmbedding(textToEmbed);
                articleChunkMediumRepository.save(new ArticleChunkMedium(article, i++, chunk, vector));
            }

            printStats();
        } catch (Exception e) {
            LOG.error("Failed to parse record as JSON or process image", e);
        } finally {
            // acknowledge exactly once here
            try {
                ack.acknowledge();
            } catch (Exception e) {
                LOG.warn("Failed to acknowledge record: {}", e.getMessage());
            }
        }
    }

    private Author createOrGetAuthor(String authorName) {
        if (authorName == null || authorName.isBlank()) {
            return null;
        }
        return authorRepository.findByName(authorName.trim()).orElseGet(() -> authorRepository.save(new Author(authorName.trim())));
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


    private LocalDate parsePublishedDate(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        String value = node.asText().trim();

        if (value.isEmpty() || value.equalsIgnoreCase("null")) {
            return null;
        }

        return LocalDate.parse(value);
    }

    private void printStats() {
        // just to show the progress every SHOW_STATS_INTERVAL_MINS minutes
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (now.isAfter(nextProgressLog)) {
            LOG.info("------------------------------------");
            LOG.info("Total response items received: {}", responseItemsReceivedTotal.get());
            LOG.info("Total response items incomplete: {}", responseItemsIncompleteTotal.get());
            LOG.info("Total response items stored: {}", responseItemsStoredTotal.get());
            LOG.info("Elapsed time: {} minutes", java.time.Duration.between(start, now).toMinutes());
            while (!now.isBefore(nextProgressLog)) {
                nextProgressLog = nextProgressLog.plusMinutes(SHOW_STATS_INTERVAL_MINS);
            }
        }
    }
}
