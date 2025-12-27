package arquivo.processor;

import arquivo.services.MetricService;
import arquivo.utils.KafkaPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@ConditionalOnProperty(name = "scribe-ref.arquivo.scribe-news-text-processor.enable", havingValue = "true")
public class TextProcessorListener {

    private static final Logger LOG = LoggerFactory.getLogger(TextProcessorListener.class);
    public static final int SHOW_STATS_INTERVAL_MINS = 1;

    private final KafkaPublisher kafkaPublisher;

    private final ObjectMapper objectMapper;

    private final MetricService metricService;

    private long responseItemsReceivedTotal, responseItemsIncompleteTotal;
    private final LocalDateTime start = LocalDateTime.now(ZoneOffset.UTC);
    private LocalDateTime nextProgressLog = start.plusMinutes(SHOW_STATS_INTERVAL_MINS);

    private final HttpClient httpClient;

    private final OpenIATextSummarizer textSummarizer;

    @Autowired
    public TextProcessorListener(Environment environment,
                                 MetricService metricService,
                                 KafkaTemplate<String, String> kafkaTemplate,
                                 @Value("${scribe-ref.arquivo.scribe-news-text-processor.kafka.to-send.topic}") String topic,
                                 @Value("$){scribe-ref.arquivo.scribe-news-text-processor.kafka.to-send.concurrency}") int concurrency) {
        this.metricService = metricService;
        this.kafkaPublisher = new KafkaPublisher(kafkaTemplate, topic, concurrency);
        this.objectMapper = new ObjectMapper();

        responseItemsIncompleteTotal = metricService.loadValue("arquivo_text_processor_response_items_incomplete_total");
        responseItemsReceivedTotal = metricService.loadValue("arquivo_text_processor_response_items_received_total");

        final String apiKey = environment.getProperty("scribe-ref.arquivo.scribe-news-text-processor.open-ai.api-key");

        this.textSummarizer = new OpenIATextSummarizer(apiKey);

        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @KafkaListener(
            topics = {"${scribe-ref.arquivo.scribe-news-text-processor.kafka.to-listen.topic}"},
            containerFactory = "kafkaListenerContainerFactory",
            concurrency = "${scribe-ref.arquivo.scribe-news-text-processor.kafka.to-listen.concurrency}")
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

            // get text processing could be done here
            final String rawText = fetchExtractedText(responseItem.get("linkToExtractedText").asText());
            LOG.debug("Fetched extracted text of length {}", rawText.length());

            // clean text: remove extra spaces, new lines, etc. could be done here
            String cleanedText = rawText.replaceAll("\\s+", " ").trim();
            LOG.debug("Cleaned text length {}", cleanedText.length());

            // use open IA to sumerize the text could be done here
            final JsonNode openIaResponse = objectMapper.readTree(textSummarizer.summarizeTextWithOpenAI(cleanedText));
            LOG.debug("OpenAI response: {}", openIaResponse.toPrettyString());

            final ObjectNode articleToExtractEmbeddding = objectMapper.createObjectNode()
                    .put("title", responseItem.get("title").asText())
                    .put("imageName", responseItem.get("imageName").asText())
                    .put("summary", openIaResponse.get("summary").asText())
                    .put("publishedDate", openIaResponse.get("publishedDate").asText())
                    .put("publishedDateConfidence", openIaResponse.get("publishedDateConfidence").asDouble())
                    .put("linkToArchive", responseItem.get("linkToArchive").asText());

            kafkaPublisher.send(articleToExtractEmbeddding);

            printStats();
        } catch (Exception e) {
            LOG.error("Failed to parse record as JSON or process image", e);
        } finally {
            // acknowledge exactly once here
            try {
                ack.acknowledge();
            } catch (Exception e) {
                LOG.error("Failed to acknowledge record: {}", e.getMessage());
            }
        }
    }

    private String fetchExtractedText(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            LOG.error("error fetching text from {}: HTTP {}", url, response.statusCode());
            throw new RuntimeException("Failed to fetch text. HTTP " + response.statusCode());
        }

        return response.body();
    }

    private void printStats() {
        // just to show the progress every SHOW_STATS_INTERVAL_MINS minutes
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (now.isAfter(nextProgressLog)) {
            metricService.updateValue("arquivo_text_processor_response_items_incomplete_total", responseItemsIncompleteTotal);
            metricService.updateValue("arquivo_text_processor_response_items_received_total", responseItemsReceivedTotal);
            LOG.info("Total response items received: {}", responseItemsReceivedTotal);
            LOG.info("Total response items incomplete: {}", responseItemsIncompleteTotal);
            LOG.info("Elapsed time: {} minutes", java.time.Duration.between(start, now).toMinutes());
            while (!now.isBefore(nextProgressLog)) {
                nextProgressLog = nextProgressLog.plusMinutes(SHOW_STATS_INTERVAL_MINS);
            }
        }
    }
}
