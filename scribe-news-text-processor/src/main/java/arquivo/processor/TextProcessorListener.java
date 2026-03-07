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

    private long responseItemsReceivedTotal, responseItemsIncompleteTotal, openAiResponseErrorsTotal, responseItemsSentToKafkaTotal, notRelevantTotal;
    private final LocalDateTime start = LocalDateTime.now(ZoneOffset.UTC);
    private LocalDateTime nextProgressLog = start.plusMinutes(SHOW_STATS_INTERVAL_MINS);

    private final HttpClient httpClient;

    private final OpenAiIntegration openAiIntegration;

    private final AuthorExtractor authorExtractor;

    @Autowired
    public TextProcessorListener(Environment environment,
                                 MetricService metricService,
                                 KafkaTemplate<String, String> kafkaTemplate,
                                 @Value("${scribe-ref.arquivo.scribe-news-text-processor.kafka.to-send.topic}") String topic,
                                 @Value("${scribe-ref.arquivo.scribe-news-text-processor.author-extractor-url}") String authorExtractorUrl) {
        this.metricService = metricService;
        this.authorExtractor = new AuthorExtractor(authorExtractorUrl);
        this.kafkaPublisher = new KafkaPublisher(kafkaTemplate, topic);
        this.objectMapper = new ObjectMapper();

        responseItemsIncompleteTotal = metricService.loadValue("arquivo_text_processor_response_items_incomplete_total");
        responseItemsReceivedTotal = metricService.loadValue("arquivo_text_processor_response_items_received_total");
        openAiResponseErrorsTotal = metricService.loadValue("arquivo_text_processor_open_ia_response_errors_total");
        responseItemsSentToKafkaTotal = metricService.loadValue("arquivo_text_processor_response_items_sent_to_kafka_total");
        notRelevantTotal = metricService.loadValue("arquivo_text_processor_summary_is_not_relevant");

        final String apiKey = environment.getProperty("scribe-ref.arquivo.scribe-news-text-processor.open-ai.api-key");

        this.openAiIntegration = new OpenAiIntegration(apiKey);

        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @KafkaListener(
            topics = {"${scribe-ref.arquivo.scribe-news-text-processor.kafka.to-listen.topic}"},
            containerFactory = "kafkaListenerContainerFactory",
            concurrency = "${scribe-ref.arquivo.scribe-news-text-processor.kafka.to-listen.concurrency}")
    public void listener(ConsumerRecord<String, String> record, Acknowledgment ack, @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {
        LOG.trace("Received on topic {} on partition {} record {}", record.topic(), partition, record.value());

        responseItemsReceivedTotal++;
        metricService.updateValue("arquivo_text_processor_response_items_received_total", 1);

        try {
            String payload = record.value();
            if (payload == null || payload.isBlank()) {
                LOG.warn("Empty payload for key {}", record.key());
                responseItemsIncompleteTotal++;
                metricService.updateValue("arquivo_text_processor_response_items_incomplete_total", 1);
                return;
            }

            JsonNode responseItem = null;
            try {
                responseItem = objectMapper.readTree(payload);
            } catch (Exception ex) {
                LOG.error("Failed to parse record as JSON: {}", payload, ex);
            }
            if (responseItem == null
                    || !responseItem.hasNonNull("linkToExtractedText")
                    || responseItem.get("linkToExtractedText").asText().isBlank()) {
                LOG.error("Incomplete response item (should not happen); missing linkToExtractedText: {}", payload);
                responseItemsIncompleteTotal++;
                metricService.updateValue("arquivo_text_processor_response_items_incomplete_total", 1);
                return;
            }

            final String rawText = fetchExtractedText(responseItem.get("linkToExtractedText").asText());

            if (!passesRelevancePreScreen(rawText, responseItem.get("person").asText())) {
                LOG.debug("Failed relevance pre-screen, skipping OpenAI summarization");
                notRelevantTotal++;
                metricService.updateValue("arquivo_text_processor_summary_is_not_relevant", 1);
                return;
            }

            // use open IA to sumerize the text could be done here
            final String openAiResponseString = openAiIntegration.summarizeText(rawText);

            JsonNode openAiResponse = null;
            try {
                openAiResponse = objectMapper.readTree(sanitizeJson(openAiResponseString));
            } catch (Exception ex) {
                LOG.error("Failed to parse JSON from OpenAI: {}", openAiResponseString, ex);
                openAiResponseErrorsTotal++;
                metricService.updateValue("arquivo_text_processor_open_ia_response_errors_total", 1);
            }
            if (openAiResponse == null) {
                LOG.error("Incomplete response item, missing linkToExtractedText: {}", payload);
                openAiResponseErrorsTotal++;
                metricService.updateValue("arquivo_text_processor_open_ia_response_errors_total", 1);
                return;
            }
            final String personName = responseItem.get("person").asText();
            if (!openAiIntegration.isAbout(openAiResponse.get("summary").asText(), personName)) {
                LOG.debug("Summary is not about: {}", personName);
                notRelevantTotal++;
                metricService.updateValue("arquivo_text_processor_summary_is_not_relevant", 1);
                return;
            }

            final String author = authorExtractor.extractAuthor(rawText).orElse(null);

            //LOG.debug("OpenAI response: {}", openAiResponse.toPrettyString());

            final ObjectNode articleToExtractEmbeddding = objectMapper.createObjectNode()
                    .put("title", responseItem.get("title").asText())
                    .put("siteId", responseItem.get("siteId").asInt())
                    .put("author", author)
                    .put("articleHash", responseItem.get("articleHash").asInt())
                    .put("originalImagePath", responseItem.get("originalImagePath").asText())
                    .put("smallImagePath", responseItem.get("smallImagePath").asText())
                    .put("linkToScreenshot", responseItem.get("linkToScreenshot").asText())
                    .put("summary", openAiResponse.get("summary").asText())
                    .put("publishedDate", openAiResponse.get("publishedDate").asText())
                    .put("publishedDateConfidence", openAiResponse.get("publishedDateConfidence").asDouble())
                    .put("linkToArchive", responseItem.get("linkToArchive").asText());

            kafkaPublisher.send(articleToExtractEmbeddding);
            responseItemsSentToKafkaTotal++;
            metricService.updateValue("arquivo_image_processor_response_items_sent_to_kafka_total", 1);
            LOG.trace("Sent to Kafka: {}", articleToExtractEmbeddding.toPrettyString());

            printStats();
        } catch (Exception e) {
            LOG.error("Failed", e);
        } finally {
            // acknowledge exactly once here
            try {
                ack.acknowledge();
            } catch (Exception e) {
                LOG.error("Failed to acknowledge record: {}", e.getMessage());
            }
        }
    }

    private String sanitizeJson(String raw) {
        if (raw == null) {
            return null;
        }

        // Remove trailing commas before } or ]
        return raw.replaceAll(",\\s*([}\\]])", "$1");
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

    private boolean passesRelevancePreScreen(String rawText, String personName) {
        if (rawText == null || rawText.isBlank()) return false;

        String lowerText = rawText.toLowerCase();
        String lowerName = personName.toLowerCase();

        // Count occurrences of the person's name
        int count = 0;
        int idx = 0;
        while ((idx = lowerText.indexOf(lowerName, idx)) != -1) {
            count++;
            idx += lowerName.length();
        }

        // Also check for last name only (e.g., "António Costa" → "Costa")
        String[] nameParts = lowerName.split("\\s+");
        String lastName = nameParts[nameParts.length - 1];
        int lastNameCount = 0;
        idx = 0;
        while ((idx = lowerText.indexOf(lastName, idx)) != -1) {
            lastNameCount++;
            idx += lastName.length();
        }

        // Require at least 3 full name mentions or 5 last name mentions
        // and name should appear in first 20% of text (headline/lead)
        int firstFifth = lowerText.length() / 5;
        boolean inLead = lowerText.substring(0, Math.min(firstFifth, lowerText.length())).contains(lowerName);

        return (count >= 3 || (lastNameCount >= 5 && inLead));
    }


    private void printStats() {
        // just to show the progress every SHOW_STATS_INTERVAL_MINS minutes
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (now.isAfter(nextProgressLog)) {
            LOG.info("------------------------------------");
            LOG.info("Total response items received: {}", responseItemsReceivedTotal);
            LOG.info("Total response items incomplete: {}", responseItemsIncompleteTotal);
            LOG.info("Total OpenAI response errors: {}", openAiResponseErrorsTotal);
            LOG.info("Total response items sent to Kafka: {}", responseItemsSentToKafkaTotal);
            LOG.info("Total summaries not relevant: {}", notRelevantTotal);
            LOG.info("Elapsed time: {} minutes", Duration.between(start, now).toMinutes());
            while (!now.isBefore(nextProgressLog)) {
                nextProgressLog = nextProgressLog.plusMinutes(SHOW_STATS_INTERVAL_MINS);
            }
        }
    }
}
