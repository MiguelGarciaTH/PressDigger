package arquivo.processor;

import arquivo.services.MetricService;
import arquivo.services.DiscardedArticleBloomFilterService;
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
import java.util.concurrent.atomic.AtomicLong;

@Component
@ConditionalOnProperty(name = "scribe-ref.arquivo.scribe-news-text-processor.enable", havingValue = "true")
public class TextProcessorListener {

    private static final Logger LOG = LoggerFactory.getLogger(TextProcessorListener.class);
    public static final int SHOW_STATS_INTERVAL_MINS = 1;
    public static final String ARQUIVO_TEXT_PROCESSOR_RESPONSE_ITEMS_INCOMPLETE_TOTAL = "arquivo_text_processor_response_items_incomplete_total";
    public static final String ARQUIVO_TEXT_PROCESSOR_RESPONSE_ITEMS_RECEIVED_TOTAL = "arquivo_text_processor_response_items_received_total";
    public static final String ARQUIVO_TEXT_PROCESSOR_OPEN_IA_RESPONSE_ERRORS_TOTAL = "arquivo_text_processor_open_ia_response_errors_total";
    public static final String ARQUIVO_TEXT_PROCESSOR_RESPONSE_ITEMS_SENT_TO_KAFKA_TOTAL = "arquivo_text_processor_response_items_sent_to_kafka_total";
    public static final String ARQUIVO_TEXT_PROCESSOR_SUMMARY_IS_NOT_RELEVANT = "arquivo_text_processor_summary_is_not_relevant";

    private final KafkaPublisher kafkaPublisher;

    private final ObjectMapper objectMapper;

    private final MetricService metricService;

    private final AtomicLong responseItemsReceivedTotal = new AtomicLong(0);
    private final AtomicLong responseItemsIncompleteTotal = new AtomicLong(0);
    private final AtomicLong openAiResponseErrorsTotal = new AtomicLong(0);
    private final AtomicLong responseItemsSentToKafkaTotal = new AtomicLong(0);
    private final AtomicLong notRelevantTotal = new AtomicLong(0);

    private final LocalDateTime start = LocalDateTime.now(ZoneOffset.UTC);
    private LocalDateTime nextProgressLog = start.plusMinutes(SHOW_STATS_INTERVAL_MINS);

    private final HttpClient httpClient;

    private final OpenAiIntegrationSummary openAiIntegrationSummary;

    private final AuthorExtractor authorExtractor;

    private final DiscardedArticleBloomFilterService discardedBloomFilter;

    @Autowired
    public TextProcessorListener(Environment environment,
                                 MetricService metricService,
                                 DiscardedArticleBloomFilterService discardedBloomFilter,
                                 KafkaTemplate<String, String> kafkaTemplate,
                                 @Value("${scribe-ref.arquivo.scribe-news-text-processor.kafka.to-send.topic}") String topic,
                                 @Value("${scribe-ref.arquivo.scribe-news-text-processor.author-extractor-url}") String authorExtractorUrl) {
        this.metricService = metricService;
        this.discardedBloomFilter = discardedBloomFilter;
        this.authorExtractor = new AuthorExtractor(authorExtractorUrl);
        this.kafkaPublisher = new KafkaPublisher(kafkaTemplate, topic);
        this.objectMapper = new ObjectMapper();

        responseItemsIncompleteTotal.set(metricService.loadValue(ARQUIVO_TEXT_PROCESSOR_RESPONSE_ITEMS_INCOMPLETE_TOTAL));
        responseItemsReceivedTotal.set(metricService.loadValue(ARQUIVO_TEXT_PROCESSOR_RESPONSE_ITEMS_RECEIVED_TOTAL));
        openAiResponseErrorsTotal.set(metricService.loadValue(ARQUIVO_TEXT_PROCESSOR_OPEN_IA_RESPONSE_ERRORS_TOTAL));
        responseItemsSentToKafkaTotal.set(metricService.loadValue(ARQUIVO_TEXT_PROCESSOR_RESPONSE_ITEMS_SENT_TO_KAFKA_TOTAL));
        notRelevantTotal.set(metricService.loadValue(ARQUIVO_TEXT_PROCESSOR_SUMMARY_IS_NOT_RELEVANT));

        final String apiKey = environment.getProperty("scribe-ref.arquivo.scribe-news-text-processor.open-ai.api-key");

        this.openAiIntegrationSummary = new OpenAiIntegrationSummary(apiKey);

        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))  // increased from 10s — arquivo.pt can be slow
                .build();
    }

    @KafkaListener(
            topics = {"${scribe-ref.arquivo.scribe-news-text-processor.kafka.to-listen.topic}"},
            containerFactory = "kafkaListenerContainerFactory",
            concurrency = "${scribe-ref.arquivo.scribe-news-text-processor.kafka.to-listen.concurrency}")
    public void listener(ConsumerRecord<String, String> record, Acknowledgment ack, @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {
        LOG.trace("Received on topic {} on partition {} record {}", record.topic(), partition, record.value());

        metricService.updateValue(ARQUIVO_TEXT_PROCESSOR_RESPONSE_ITEMS_RECEIVED_TOTAL, responseItemsReceivedTotal.incrementAndGet());

        try {
            String payload = record.value();
            if (payload == null || payload.isBlank()) {
                LOG.warn("Empty payload for key {}", record.key());
                metricService.updateValue(ARQUIVO_TEXT_PROCESSOR_RESPONSE_ITEMS_INCOMPLETE_TOTAL, responseItemsIncompleteTotal.incrementAndGet());
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
                metricService.updateValue(ARQUIVO_TEXT_PROCESSOR_RESPONSE_ITEMS_INCOMPLETE_TOTAL, responseItemsIncompleteTotal.incrementAndGet());
                return;
            }

            final String rawText = fetchExtractedText(responseItem.get("linkToExtractedText").asText());

            if (!passesRelevancePreScreen(rawText, responseItem.get("person").asText())) {
                LOG.debug("Failed relevance pre-screen, skipping OpenAI summarization");
                metricService.updateValue(ARQUIVO_TEXT_PROCESSOR_SUMMARY_IS_NOT_RELEVANT, notRelevantTotal.incrementAndGet());
                discardedBloomFilter.markAsDiscarded(responseItem.get("articleHash").asInt());
                return;
            }

            // use open IA to sumerize the text could be done here
            final String openAiResponseString = openAiIntegrationSummary.summarizeText(rawText);

            JsonNode openAiResponse = null;
            try {
                openAiResponse = objectMapper.readTree(sanitizeJson(openAiResponseString));
            } catch (Exception ex) {
                LOG.error("Failed to parse JSON from OpenAI: {}", openAiResponseString, ex);
                metricService.updateValue(ARQUIVO_TEXT_PROCESSOR_OPEN_IA_RESPONSE_ERRORS_TOTAL, openAiResponseErrorsTotal.incrementAndGet());
                return;
            }
            if (openAiResponse == null) {
                LOG.error("Incomplete response item, missing linkToExtractedText: {}", payload);
                metricService.updateValue(ARQUIVO_TEXT_PROCESSOR_OPEN_IA_RESPONSE_ERRORS_TOTAL, openAiResponseErrorsTotal.incrementAndGet());
                return;
            }
            final String personName = responseItem.get("person").asText();
            if (!openAiIntegrationSummary.isAbout(openAiResponse.get("summary").asText(), personName)) {
                LOG.debug("Summary is not about: {}", personName);
                metricService.updateValue(ARQUIVO_TEXT_PROCESSOR_OPEN_IA_RESPONSE_ERRORS_TOTAL, openAiResponseErrorsTotal.incrementAndGet());
                discardedBloomFilter.markAsDiscarded(responseItem.get("articleHash").asInt());
                return;
            }

            final String author = authorExtractor.extractAuthor(rawText).orElse(null);

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
            metricService.updateValue("arquivo_image_processor_response_items_sent_to_kafka_total", responseItemsSentToKafkaTotal.incrementAndGet());
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
        final int maxAttempts = 3;
        long backoffMs = 2_000;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(45))   // increased from 20s
                        .GET()
                        .build();

                HttpResponse<String> response =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return response.body();
                }

                // 4xx = permanent error (bad URL, auth, etc.) — don't waste retries
                if (response.statusCode() >= 400 && response.statusCode() < 500) {
                    throw new RuntimeException("Permanent HTTP " + response.statusCode() + " for " + url);
                }

                // 5xx = transient server error — retry
                LOG.warn("HTTP {} from {} (attempt {}/{}), retrying...",
                        response.statusCode(), url, attempt, maxAttempts);
                lastException = new RuntimeException("HTTP " + response.statusCode());

            } catch (java.net.http.HttpTimeoutException e) {
                lastException = e;
                LOG.warn("Timeout fetching {} (attempt {}/{}): {}", url, attempt, maxAttempts, e.getMessage());
            } catch (RuntimeException e) {
                throw e; // permanent errors bubble up immediately — no retry
            }

            if (attempt < maxAttempts) {
                LOG.info("Waiting {}ms before retry attempt {}/{} for {}", backoffMs, attempt + 1, maxAttempts, url);
                Thread.sleep(backoffMs);
                backoffMs *= 2;  // exponential backoff: 2s → 4s → 8s
            }
        }

        throw new RuntimeException(
                "Failed to fetch text after " + maxAttempts + " attempts: " + url, lastException);
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
        //int firstFifth = lowerText.length() / 5;
        //boolean inLead = lowerText.substring(0, Math.min(firstFifth, lowerText.length())).contains(lowerName);

        return (count >= 3 || (lastNameCount >= 5));
    }


    private void printStats() {
        // just to show the progress every SHOW_STATS_INTERVAL_MINS minutes
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (now.isAfter(nextProgressLog)) {
            LOG.info("------------------------------------");
            LOG.info("Total response items received: {}", responseItemsReceivedTotal.get());
            LOG.info("Total response items incomplete: {}", responseItemsIncompleteTotal.get());
            LOG.info("Total OpenAI response errors: {}", openAiResponseErrorsTotal.get());
            LOG.info("Total response items sent to Kafka: {}", responseItemsSentToKafkaTotal.get());
            LOG.info("Total summaries not relevant: {}", notRelevantTotal.get());
            LOG.info("Elapsed time: {} minutes", Duration.between(start, now).toMinutes());
            while (!now.isBefore(nextProgressLog)) {
                nextProgressLog = nextProgressLog.plusMinutes(SHOW_STATS_INTERVAL_MINS);
            }
        }
    }
}
