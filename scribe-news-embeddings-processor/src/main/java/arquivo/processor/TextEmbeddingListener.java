package arquivo.processor;

import arquivo.model.Article;
import arquivo.model.ArticleChunk;
import arquivo.repository.ArticleChunkRepository;
import arquivo.repository.ArticleRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "scribe-ref.arquivo.scribe-news-embeddings-processor.enable", havingValue = "true")
public class TextEmbeddingListener {

    private static final Logger LOG = LoggerFactory.getLogger(TextEmbeddingListener.class);
    public static final int SHOW_STATS_INTERVAL_MINS = 1;

    private final ObjectMapper objectMapper;

    private final MetricService metricService;

    private long responseItemsIncompleteTotal, responseItemsReceivedTotal;
    private final LocalDateTime start = LocalDateTime.now(ZoneOffset.UTC);
    private LocalDateTime nextProgressLog = start.plusMinutes(SHOW_STATS_INTERVAL_MINS);
    private final TextEmbeddingClient textEmbeddingClient;

    private static final Pattern PARAGRAPH_SPLIT = Pattern.compile("\\R\\s*\\R");

    private final ArticleRepository articleRepository;
    private final ArticleChunkRepository articleChunkRepository;

    @Autowired
    public TextEmbeddingListener(Environment environment,
                                 MetricService metricService,
                                 ArticleRepository articleRepository,
                                 ArticleChunkRepository articleChunkRepository) {
        this.metricService = metricService;
        this.objectMapper = new ObjectMapper();
        this.articleRepository = articleRepository;
        this.articleChunkRepository = articleChunkRepository;
        final String url = environment.getProperty("scribe-ref.arquivo.scribe-embeddings-processor.embedding-service-url");
        this.textEmbeddingClient = new TextEmbeddingClient(url, objectMapper, false);

        responseItemsIncompleteTotal = metricService.loadValue("arquivo_embeddings_processor_response_items_incomplete_total");
        responseItemsReceivedTotal = metricService.loadValue("arquivo_embeddings_processor_response_items_received_total");
    }

    @KafkaListener(
            topics = {"${scribe-ref.arquivo.scribe-news-embeddings-processor.kafka.to-listen.topic}"},
            containerFactory = "kafkaListenerContainerFactory",
            concurrency = "${scribe-ref.arquivo.scribe-news-embeddings-processor.kafka.to-listen.concurrency}")
    public void listener(ConsumerRecord<String, String> record, Acknowledgment ack, @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {
        LOG.debug("Received on topic {} on partition {} record {}", record.topic(), partition, record.value());
        responseItemsReceivedTotal++;

        try {
            String payload = record.value();
            if (payload == null || payload.isBlank()) {
                LOG.warn("Empty payload for key {}", record.key());
                responseItemsIncompleteTotal++;
                return;
            }

            final JsonNode responseItem = objectMapper.readTree(payload);

            final String summary = responseItem.get("summary").asText();
            final String[] summaryParagraphs = PARAGRAPH_SPLIT.split(summary);

            final Article article = articleRepository.save(
                    new Article(responseItem.get("title").asText(),
                            !responseItem.get("publishedDate").isNull() ? LocalDate.parse(responseItem.get("publishedDate").asText()) : null,
                            responseItem.get("publishedDateConfidence").asDouble(),
                            responseItem.get("linkToArchive").asText(),
                            UrlNormalizer.normalize(responseItem.get("linkToArchive").asText()),
                            responseItem.get("imageName").asText()
                    )
            );

            int i = 0;
            for (String paragraph : summaryParagraphs) {
                final JsonNode embeddingResponseParagraph = textEmbeddingClient.getEmbeddings(paragraph).get("embedding");
                articleChunkRepository.save(new ArticleChunk(article, i++, paragraph, textEmbeddingClient.toFloatArray(embeddingResponseParagraph)));
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

    private void printStats() {
        // just to show the progress every SHOW_STATS_INTERVAL_MINS minutes
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (now.isAfter(nextProgressLog)) {
            metricService.updateValue("arquivo_embeddings_processor_response_items_incomplete_total", responseItemsIncompleteTotal);
            metricService.updateValue("arquivo_embeddings_processor_response_items_received_total", responseItemsReceivedTotal);

            LOG.info("Total response items incomplete: {}", responseItemsIncompleteTotal);
            LOG.info("Elapsed time: {} minutes", java.time.Duration.between(start, now).toMinutes());
            while (!now.isBefore(nextProgressLog)) {
                nextProgressLog = nextProgressLog.plusMinutes(SHOW_STATS_INTERVAL_MINS);
            }
        }
    }
}
