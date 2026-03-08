package arquivo.processor;

import arquivo.services.MetricService;
import arquivo.utils.KafkaPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@ConditionalOnProperty(name = "scribe-ref.arquivo.scribe-news-image-processor.enable", havingValue = "true")
public class ImageProcessorListener {

    private static final Logger LOG = LoggerFactory.getLogger(ImageProcessorListener.class);
    public static final int SHOW_STATS_INTERVAL_MINS = 1;

    public static final String ARQUIVO_IMAGE_PROCESSOR_RECEIVED_MESSAGES_TOTAL = "arquivo_image_processor_received_messages_total";
    public static final String ARQUIVO_IMAGE_PROCESSOR_BLANK_IMAGES_TOTAL = "arquivo_image_processor_blank_images_total";
    public static final String ARQUIVO_IMAGE_PROCESSOR_RESPONSE_ITEMS_INCOMPLETE_TOTAL = "arquivo_image_processor_response_items_incomplete_total";
    public static final String ARQUIVO_IMAGE_PROCESSOR_DUPLICATE_FILES_TOTAL = "arquivo_image_processor_duplicate_files_total";
    public static final String ARQUIVO_IMAGE_PROCESSOR_NO_TEXT_IMAGES_TOTAL = "arquivo_image_processor_no_text_images_total";
    public static final String ARQUIVO_IMAGE_PROCESSOR_RESPONSE_ITEMS_SENT_TO_KAFKA_TOTAL = "arquivo_image_processor_response_items_sent_to_kafka_total";

    private final KafkaPublisher kafkaPublisher;

    private final ObjectMapper objectMapper;

    private final MetricService metricService;

    private final ImageTextDetector imageTextDetector;

    private long responseItemsReceivedTotal, blankImagesTotal, noTextImageTotal, responseItemsIncompleteTotal, duplicateFilesTotal, responseItemsSentToKafkaTotal;
    private final LocalDateTime start = LocalDateTime.now(ZoneOffset.UTC);
    private LocalDateTime nextProgressLog = start.plusMinutes(SHOW_STATS_INTERVAL_MINS);

    private final Path directory;

    @Value("${scribe-ref.arquivo.scribe-news-image-processor.thumbnail.quality:0.8}")
    private double thumbnailQuality;

    @Value("${scribe-ref.arquivo.scribe-news-image-processor.http.connect-timeout-ms:5000}")
    private int httpConnectTimeoutMs;

    @Value("${scribe-ref.arquivo.scribe-news-image-processor.http.read-timeout-ms:10000}")
    private int httpReadTimeoutMs;

    @Value("${scribe-ref.arquivo.scribe-news-image-processor.http.max-retries:3}")
    private int maxRetries;

    @Value("${scribe-ref.arquivo.scribe-news-image-processor.http.initial-backoff-ms:1000}")
    private long initialBackoffMs;

    @Value("${scribe-ref.arquivo.scribe-news-image-processor.http.backoff-multiplier:2.0}")
    private double backoffMultiplier;

    @Autowired
    public ImageProcessorListener(MetricService metricService,
                                  KafkaTemplate<String, String> kafkaTemplate,
                                  ImageTextDetector imageTextDetector,
                                  @Value("${scribe-ref.arquivo.scribe-news-image-processor.image-path-directory}") String imagePathDirectory,
                                  @Value("${scribe-ref.arquivo.scribe-news-image-processor.kafka.to-send.topic}") String topic) {
        this.metricService = metricService;
        this.objectMapper = new ObjectMapper();
        this.imageTextDetector = imageTextDetector;
        this.kafkaPublisher = new KafkaPublisher(kafkaTemplate, topic);

        responseItemsReceivedTotal = metricService.loadValue(ARQUIVO_IMAGE_PROCESSOR_RECEIVED_MESSAGES_TOTAL);
        blankImagesTotal = metricService.loadValue(ARQUIVO_IMAGE_PROCESSOR_BLANK_IMAGES_TOTAL);
        responseItemsIncompleteTotal = metricService.loadValue(ARQUIVO_IMAGE_PROCESSOR_RESPONSE_ITEMS_INCOMPLETE_TOTAL);
        duplicateFilesTotal = metricService.loadValue(ARQUIVO_IMAGE_PROCESSOR_DUPLICATE_FILES_TOTAL);
        noTextImageTotal = metricService.loadValue(ARQUIVO_IMAGE_PROCESSOR_NO_TEXT_IMAGES_TOTAL);
        responseItemsSentToKafkaTotal = metricService.loadValue(ARQUIVO_IMAGE_PROCESSOR_RESPONSE_ITEMS_SENT_TO_KAFKA_TOTAL);

        directory = Paths.get(imagePathDirectory).toAbsolutePath().normalize();
        LOG.info("Configured image directory: {}", directory);

        // ensure required subdirectories exist at startup to avoid FileNotFoundException
        try {
            Files.createDirectories(directory.resolve("original"));
            Files.createDirectories(directory.resolve("small"));
        } catch (IOException e) {
            LOG.error("Could not create image directories under {}: {}", directory, e.getMessage());
        }
    }

    @KafkaListener(
            topics = {"${scribe-ref.arquivo.scribe-news-image-processor.kafka.to-listen.topic}"},
            containerFactory = "kafkaListenerContainerFactory",
            concurrency = "${scribe-ref.arquivo.scribe-news-image-processor.kafka.to-listen.concurrency}")
    public void listener(ConsumerRecord<String, String> record, Acknowledgment ack, @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {
        LOG.trace("Received on topic {} on partition {} record {}", record.topic(), partition, record.value());
        metricService.updateValue(ARQUIVO_IMAGE_PROCESSOR_RECEIVED_MESSAGES_TOTAL, responseItemsReceivedTotal++);

        try {
            String payload = record.value();
            if (payload == null || payload.isBlank()) {
                LOG.warn("Empty payload for key {}", record.key());
                metricService.updateValue(ARQUIVO_IMAGE_PROCESSOR_RESPONSE_ITEMS_INCOMPLETE_TOTAL, responseItemsIncompleteTotal++);
                return;
            }

            final JsonNode responseItem = objectMapper.readTree(payload);

            final int articleHash = responseItem.get("articleHash").asInt();
            final String fileName = articleHash + ".png";

            // quick skip if both files already exist (saves expensive processing)
            final Path originalOutputPath = directory.resolve("original").resolve(fileName);
            final Path smallOutputPath = directory.resolve("small").resolve(fileName);

            boolean isDuplicate = Files.exists(originalOutputPath);
            if (isDuplicate) {
                LOG.debug("Skipping processing for {} because outputs exist", originalOutputPath);
                metricService.updateValue(ARQUIVO_IMAGE_PROCESSOR_DUPLICATE_FILES_TOTAL, duplicateFilesTotal++);
            } else {
                // process only if not duplicate
                final BufferedImage image = getImage(responseItem.get("linkToScreenshot").asText());
                if (image == null) {
                    return;
                }

                processImage(originalOutputPath, image);
                createSmallImage(smallOutputPath, image.getSubimage(0, 0, image.getWidth(), Math.min(image.getHeight() / 2, (image.getWidth() + (image.getWidth() / 2)))));
                LOG.trace("Processed image stored {}", originalOutputPath);
            }

            final ObjectNode articleToTextSummary = objectMapper.createObjectNode()
                    .put("title", responseItem.get("title").asText())
                    .put("siteId", responseItem.get("siteId").asInt())
                    .put("person", responseItem.get("person").asText())
                    .put("articleHash", responseItem.get("articleHash").asInt())
                    .put("linkToArchive", responseItem.get("linkToArchive").asText())
                    .put("linkToExtractedText", responseItem.get("linkToExtractedText").asText())
                    .put("originalImagePath", originalOutputPath.toString())
                    .put("smallImagePath", smallOutputPath.toString())
                    .put("linkToScreenshot", responseItem.get("linkToScreenshot").asText());

            kafkaPublisher.send(articleToTextSummary);
            metricService.updateValue(ARQUIVO_IMAGE_PROCESSOR_RESPONSE_ITEMS_SENT_TO_KAFKA_TOTAL, responseItemsSentToKafkaTotal++);
            LOG.trace("Sent title {} to text summary topic", responseItem.get("title").asText());

            printStats();
            ack.acknowledge();
        } catch (Exception e) {
            LOG.error("Failed to process record", e);
            throw new RuntimeException(e); // let DefaultErrorHandler retry
            // do NOT ack — error handler will retry or send to DLT
        }
    }

    private BufferedImage getImage(String imageUrl) {
        final URI uri = URI.create(imageUrl);
        final URL url;
        try {
            url = uri.toURL();
        } catch (MalformedURLException e) {
            LOG.error("Invalid url {}", e.getMessage());
            metricService.updateValue(ARQUIVO_IMAGE_PROCESSOR_RESPONSE_ITEMS_INCOMPLETE_TOTAL, responseItemsIncompleteTotal++);
            return null;
        }

        BufferedImage image;
        try (InputStream in = openUrlStreamWithTimeouts(url)) {
            image = ImageIO.read(in);
        } catch (Exception e) {
            LOG.error("Failed to fetch image: {}", e.getMessage());
            responseItemsIncompleteTotal++;
            metricService.updateValue(ARQUIVO_IMAGE_PROCESSOR_RESPONSE_ITEMS_INCOMPLETE_TOTAL, 1);
            return null;
        }

        if (image == null) {
            LOG.warn("ImageIO.read returned null for URL {}", imageUrl);
            metricService.updateValue(ARQUIVO_IMAGE_PROCESSOR_RESPONSE_ITEMS_INCOMPLETE_TOTAL, responseItemsIncompleteTotal++);
            return null;
        }

        // Check if truly blank (uniform color)
        if (ImageBlankDetector.isBlank(image, 30, 0.20, 5)) {
            metricService.updateValue(ARQUIVO_IMAGE_PROCESSOR_BLANK_IMAGES_TOTAL, blankImagesTotal++);
            return null;
        }

        if (!imageTextDetector.hasText(image, 250)) {
            metricService.updateValue(ARQUIVO_IMAGE_PROCESSOR_NO_TEXT_IMAGES_TOTAL, noTextImageTotal++);
            return null;
        }

        return image;
    }

    // helper to open URL input stream with configured timeouts
    private InputStream openUrlStreamWithTimeouts(URL url) throws IOException {
        IOException lastException = null;
        long backoff = initialBackoffMs;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                URLConnection conn = url.openConnection();
                conn.setConnectTimeout(httpConnectTimeoutMs);
                conn.setReadTimeout(httpReadTimeoutMs);
                return conn.getInputStream();
            } catch (IOException e) {
                lastException = e;
                LOG.warn("Attempt {}/{} failed for URL {}: {}. Retrying in {} ms...",
                        attempt, maxRetries, url, e.getMessage(), backoff);

                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted during retry backoff", ie);
                    }
                    backoff = (long) (backoff * backoffMultiplier);
                }
            }
        }

        throw new IOException("Failed to fetch URL after " + maxRetries + " attempts: " + url, lastException);
    }


    private void processImage(Path originalOutputPath, BufferedImage croppedImage) throws Exception {

        // Write to file
        try {
            Files.createDirectories(originalOutputPath.getParent());
            boolean wrote = ImageIO.write(croppedImage, "png", originalOutputPath.toFile());
            if (!wrote) {
                LOG.error("ImageIO.write returned false for {}", originalOutputPath);
                throw new IOException("ImageIO.write returned false for " + originalOutputPath);
            }
        } catch (IOException e) {
            LOG.error("Failed to write original image to {}: {}", originalOutputPath, e.getMessage());
            throw new IOException("Failed to write original image to " + originalOutputPath + ": " + e.getMessage(), e);
        }
    }

    private void createSmallImage(Path smallOutputPath, BufferedImage croppedImage) throws Exception {
        // Create thumbnail from cropped image
        try {
            final BufferedImage dest = croppedImage.getSubimage(
                    0, 0,
                    croppedImage.getWidth(),
                    Math.min(croppedImage.getHeight() / 2, (croppedImage.getWidth() + (croppedImage.getWidth() / 2)))
            );
            Files.createDirectories(smallOutputPath.getParent());
            Thumbnails.of(dest)
                    .size(dest.getWidth(), dest.getWidth())
                    .outputFormat("png")
                    .outputQuality(thumbnailQuality)
                    .toFile(smallOutputPath.toFile());
        } catch (IOException e) {
            LOG.error("Failed to write thumbnail to {}: {}", smallOutputPath, e.getMessage());
            throw new IOException("Failed to write thumbnail to " + smallOutputPath + ": " + e.getMessage(), e);
        }
    }

    private void printStats() {
        // just to show the progress every SHOW_STATS_INTERVAL_MINS minutes
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (now.isAfter(nextProgressLog)) {
            LOG.info("------------------------------------");
            LOG.info("Total received messages: {}", responseItemsReceivedTotal);
            LOG.info("Total blank images: {}", blankImagesTotal);
            LOG.info("Total no-text images: {}", noTextImageTotal);
            LOG.info("Total response items incomplete: {}", responseItemsIncompleteTotal);
            LOG.info("Total duplicate files skipped: {}", duplicateFilesTotal);
            LOG.info("Total response items sent to Kafka: {}", responseItemsSentToKafkaTotal);
            LOG.info("Elapsed time: {} minutes", java.time.Duration.between(start, now).toMinutes());
            while (!now.isBefore(nextProgressLog)) {
                nextProgressLog = nextProgressLog.plusMinutes(SHOW_STATS_INTERVAL_MINS);
            }
        }
    }
}
