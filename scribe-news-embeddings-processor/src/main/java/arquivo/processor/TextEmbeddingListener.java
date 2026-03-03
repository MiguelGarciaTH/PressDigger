package arquivo.processor;

import arquivo.model.*;
import arquivo.repository.*;
import arquivo.services.MetricService;
import arquivo.services.TextEmbeddingClient;
import arquivo.utils.UrlNormalizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
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

@Component
@ConditionalOnProperty(name = "scribe-ref.arquivo.scribe-news-embeddings-processor.enable", havingValue = "true")
public class TextEmbeddingListener {

    private static final Logger LOG = LoggerFactory.getLogger(TextEmbeddingListener.class);
    public static final int SHOW_STATS_INTERVAL_MINS = 1;

    private final ObjectMapper objectMapper;

    private final MetricService metricService;

    private long responseItemsIncompleteTotal, responseItemsReceivedTotal, responseItemsStoredTotal;
    private final LocalDateTime start = LocalDateTime.now(ZoneOffset.UTC);
    private LocalDateTime nextProgressLog = start.plusMinutes(SHOW_STATS_INTERVAL_MINS);
    private final TextEmbeddingClient textEmbeddingClient;
    private final YakeClient yakeClient;

    private final ArticleRepository articleRepository;
    private final ArticleChunkRepository articleChunkRepository;
    private final ArticleChunkMediumRepository articleChunkMediumRepository;
    private final SiteRepository siteRepository;
    private final KeywordRepository keywordRepository;
    private final ArticleKeywordScoreRepository articleKeywordScoreRepository;
    private final AuthorRepository authorRepository;

    @Autowired
    public TextEmbeddingListener(Environment environment,
                                 MetricService metricService,
                                 ArticleRepository articleRepository,
                                 ArticleChunkRepository articleChunkRepository,
                                 ArticleChunkMediumRepository articleChunkMediumRepository,
                                 SiteRepository siteRepository,
                                 KeywordRepository keywordRepository,
                                 ArticleKeywordScoreRepository articleKeywordScoreRepository,
                                 AuthorRepository authorRepository) {
        this.metricService = metricService;
        this.objectMapper = new ObjectMapper();
        this.articleRepository = articleRepository;
        this.articleChunkRepository = articleChunkRepository;
        this.articleChunkMediumRepository = articleChunkMediumRepository;
        this.siteRepository = siteRepository;
        this.keywordRepository = keywordRepository;
        this.articleKeywordScoreRepository = articleKeywordScoreRepository;
        this.authorRepository = authorRepository;
        final String url = environment.getProperty("scribe-ref.arquivo.scribe-embeddings-processor.embedding-service-url");
        this.textEmbeddingClient = new TextEmbeddingClient(url, objectMapper, false);
        this.yakeClient = new YakeClient("http://localhost:8002");

        responseItemsIncompleteTotal = metricService.loadValue("arquivo_embeddings_processor_response_items_incomplete_total");
        responseItemsReceivedTotal = metricService.loadValue("arquivo_embeddings_processor_response_items_received_total");
        responseItemsStoredTotal = metricService.loadValue("arquivo_embeddings_processor_response_items_stored_total");
    }

    @KafkaListener(
            topics = {"${scribe-ref.arquivo.scribe-news-embeddings-processor.kafka.to-listen.topic}"},
            containerFactory = "kafkaListenerContainerFactory",
            concurrency = "${scribe-ref.arquivo.scribe-news-embeddings-processor.kafka.to-listen.concurrency}")
    public void listener(ConsumerRecord<String, String> record, Acknowledgment ack, @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {
        LOG.trace("Received on topic {} on partition {} record {}", record.topic(), partition, record.value());
        responseItemsReceivedTotal++;
        metricService.updateValue("arquivo_embeddings_processor_response_items_received_total", 1);

        try {
            String payload = record.value();
            if (payload == null || payload.isBlank()) {
                LOG.warn("Empty payload for key {}", record.key());
                responseItemsIncompleteTotal++;
                metricService.updateValue("arquivo_embeddings_processor_response_items_incomplete_total", 1);
                return;
            }

            final JsonNode responseItem = objectMapper.readTree(payload);

            final Site site = siteRepository.findById(responseItem.get("siteId").asInt()).orElse(null);
            if (site == null) {
                LOG.warn("Site with id {} not found, skipping article {}", responseItem.get("siteId").asInt(), responseItem.get("title").asText());
                responseItemsIncompleteTotal++;
                metricService.updateValue("arquivo_embeddings_processor_response_items_incomplete_total", 1);
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
            responseItemsStoredTotal++;
            metricService.updateValue("arquivo_embeddings_processor_response_items_stored_total", 1);
            LOG.trace("Stored article {} with id {}", article.getTitle(), article.getId());

            // create keywords for the article using YAKE
            final List<YakeClient.Keyword> extractedKeywords = yakeClient.extract(summary, "pt", 5, 2);
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
            final List<String> chunks = createChanksBySentence(summary);
            final List<String> chunksMedium = createChunksByThreeSentences(summary);

            // Process both in parallel using CompletableFuture
            CompletableFuture<Void> processSmallChunks = CompletableFuture.runAsync(() -> {
                int i = 0;
                for (String chunk : chunks) {
                    final JsonNode embeddingResponseParagraph = textEmbeddingClient.getEmbeddings(chunk).get("embedding");
                    articleChunkRepository.save(new ArticleChunk(article, i++, chunk, textEmbeddingClient.toFloatArray(embeddingResponseParagraph)));
                }
            });

            CompletableFuture<Void> processMediumChunks = CompletableFuture.runAsync(() -> {
                int i = 0;
                for (String chunk : chunksMedium) {
                    final JsonNode embeddingResponseParagraph = textEmbeddingClient.getEmbeddings(chunk).get("embedding");
                    articleChunkMediumRepository.save(new ArticleChunkMedium(article, i++, chunk, textEmbeddingClient.toFloatArray(embeddingResponseParagraph)));
                }
            });

            // Wait for both to complete
            CompletableFuture.allOf(processSmallChunks, processMediumChunks).join();

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

    public List<String> createChanksBySentence(String summary) {
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
        return sentences;
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
            LOG.info("Total response items received: {}", responseItemsReceivedTotal);
            LOG.info("Total response items incomplete: {}", responseItemsIncompleteTotal);
            LOG.info("Total response items stored: {}", responseItemsStoredTotal);
            LOG.info("Elapsed time: {} minutes", java.time.Duration.between(start, now).toMinutes());
            while (!now.isBefore(nextProgressLog)) {
                nextProgressLog = nextProgressLog.plusMinutes(SHOW_STATS_INTERVAL_MINS);
            }
        }
    }
}
