package arquivo.processor;

import arquivo.services.MetricService;
import com.fasterxml.jackson.core.JsonProcessingException;
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

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${scribe-ref.arquivo.scribe-news-image-processor.kafka.to-listen.topic}")
    private String topicToListen;

    @Value("${scribe-ref.arquivo.scribe-news-image-processor.kafka.to-listen.concurrency}")
    private int concurrencyToListen;

    @Value("${scribe-ref.arquivo.scribe-news-image-processor.kafka.to-send.topic}")
    private String topic;

    @Value("${scribe-ref.arquivo.scribe-news-image-processor.kafka.to-send.concurrency}")
    private int concurrency;

    private final ObjectMapper objectMapper;

    private final MetricService metricService;

    private long blankImagesTotal, responseItemsIncompleteTotal, duplicateFilesTotal;
    private final LocalDateTime start = LocalDateTime.now(ZoneOffset.UTC);
    private LocalDateTime nextProgressLog = start.plusMinutes(SHOW_STATS_INTERVAL_MINS);

    private final Path directory;

    // new configurable optimizations
    @Value("${scribe-ref.arquivo.scribe-news-image-processor.thumbnail.max-size:800}")
    private int thumbnailMaxSize;

    @Value("${scribe-ref.arquivo.scribe-news-image-processor.thumbnail.quality:0.8}")
    private double thumbnailQuality;

    @Value("${scribe-ref.arquivo.scribe-news-image-processor.skip-if-exists:true}")
    private boolean skipIfExists;

    @Value("${scribe-ref.arquivo.scribe-news-image-processor.http.connect-timeout-ms:5000}")
    private int httpConnectTimeoutMs;

    @Value("${scribe-ref.arquivo.scribe-news-image-processor.http.read-timeout-ms:10000}")
    private int httpReadTimeoutMs;

    @Autowired
    public ImageProcessorListener(MetricService metricService,
                                  KafkaTemplate<String, String> kafkaTemplate,
                                  @Value("${scribe-ref.arquivo.scribe-news-image-processor.image-path-directory}") String imagePathDirectory) {
        this.metricService = metricService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = new ObjectMapper();

        blankImagesTotal = metricService.loadValue("arquivo_image_processor_blank_images_total");
        responseItemsIncompleteTotal = metricService.loadValue("arquivo_image_processor_response_items_incomplete_total");
        duplicateFilesTotal = metricService.loadValue("arquivo_image_processor_duplicate_files_total");

        directory = Paths.get(imagePathDirectory).toAbsolutePath().normalize();
        LOG.info("Configured image directory: {}", directory);

        // ensure required subdirectories exist at startup to avoid FileNotFoundException
        try {
            Files.createDirectories(directory.resolve("original"));
            Files.createDirectories(directory.resolve("small"));
        } catch (IOException e) {
            LOG.warn("Could not create image directories under {}: {}", directory, e.getMessage());
        }
    }

    @KafkaListener(
            topics = {"${scribe-ref.arquivo.scribe-news-image-processor.kafka.to-listen.topic}"},
            containerFactory = "kafkaListenerContainerFactory",
            concurrency = "${scribe-ref.arquivo.scribe-news-image-processor.kafka.to-listen.concurrency}")
    public void listener(ConsumerRecord<String, String> record, Acknowledgment ack, @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {
        LOG.debug("Received on topic {} on partition {} record {}", record.topic(), partition, record.value());

        try {
            String payload = record.value();
            if (payload == null || payload.isBlank()) {
                LOG.warn("Empty payload for key {}", record.key());
                responseItemsIncompleteTotal++;
                return;
            }

            final JsonNode responseItem = objectMapper.readTree(payload);
            if (responseItem.has("linkToScreenshot") && !responseItem.get("linkToScreenshot").isNull()) {
                final String imageUrl = responseItem.get("linkToScreenshot").asText();
                final URL url = new URL(imageUrl);

                // open connection with timeouts and read once, reuse the BufferedImage
                BufferedImage image;
                try (InputStream in = openUrlStreamWithTimeouts(url)) {
                    image = ImageIO.read(in);
                } catch (Exception e) {
                    LOG.warn("Failed to fetch image: {}", e.getMessage());
                    responseItemsIncompleteTotal++;
                    return;
                }

                if (image == null) {
                    // ImageIO.read may return null for unsupported formats; treat as incomplete
                    LOG.warn("ImageIO.read returned null for URL {}", imageUrl);
                    responseItemsIncompleteTotal++;
                    return;
                }

                if (ImageBlankDetector.isBlank(image, 1, 0.01, 2)) {
                    blankImagesTotal++;
                    return;
                }

                // process using the already-read BufferedImage (no re-download)
                final String imageName = processImage(url, image);

                final ObjectNode articleToTextSummary = objectMapper.createObjectNode()
                        .put("title", responseItem.get("title").asText())
                        .put("linkToArchive", responseItem.get("linkToArchive").asText())
                        .put("linkToExtractedText", responseItem.get("linkToExtractedText").asText())
                        .put("imageName", imageName);

                publishToKafka(articleToTextSummary);

            } else {
                responseItemsIncompleteTotal++;
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

    int roundRobinIndex = 0;

    private void publishToKafka(JsonNode responseItem) {
        try {
            kafkaTemplate.send(topic, roundRobinIndex, "" + roundRobinIndex, objectMapper.writeValueAsString(responseItem));
            roundRobinIndex++;
            LOG.debug("Sent to topic {} and partition value={}", topic, responseItem);
            if (roundRobinIndex == concurrency) {
                roundRobinIndex = 0;
            }
        } catch (JsonProcessingException e) {
            LOG.warn("Error processing item: {}", responseItem.toPrettyString());
        }
    }

    // helper to open URL input stream with configured timeouts
    private InputStream openUrlStreamWithTimeouts(URL url) throws IOException {
        URLConnection conn = url.openConnection();
        conn.setConnectTimeout(httpConnectTimeoutMs);
        conn.setReadTimeout(httpReadTimeoutMs);
        return conn.getInputStream();
    }

    private String processImage(URL imageUrl, BufferedImage image) throws Exception {
        final String fileName = (imageUrl.getPath().hashCode() & Integer.MAX_VALUE) + ".png";
        final Path originalOutputPath = directory.resolve("original").resolve(fileName);
        final Path smallOutputPath = directory.resolve("small").resolve(fileName);

        // quick skip if both files already exist (saves expensive processing)
        if (skipIfExists && Files.exists(originalOutputPath) && Files.exists(smallOutputPath)) {
            LOG.debug("Skipping processing for {} because outputs exist", fileName);
            duplicateFilesTotal++;
            return null;
        }

        // write original (ensure parent exists)
        try {
            Files.createDirectories(originalOutputPath.getParent());
            boolean wrote = ImageIO.write(image, "png", originalOutputPath.toFile());
            if (!wrote) {
                throw new IOException("ImageIO.write returned false for " + originalOutputPath);
            }
        } catch (IOException e) {
            throw new IOException("Failed to write original image to " + originalOutputPath + ": " + e.getMessage(), e);
        }

        // create thumbnail from the already-loaded BufferedImage (avoids re-downloading)
        try {
            Files.createDirectories(smallOutputPath.getParent());
            Thumbnails.of(image)
                    .size(thumbnailMaxSize, thumbnailMaxSize)
                    .outputFormat("png")
                    .outputQuality(thumbnailQuality)
                    .toFile(smallOutputPath.toFile());
        } catch (IOException e) {
            throw new IOException("Failed to write thumbnail to " + smallOutputPath + ": " + e.getMessage(), e);
        }
        return fileName;
    }

    private void printStats() {
        // just to show the progress every SHOW_STATS_INTERVAL_MINS minutes
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (now.isAfter(nextProgressLog)) {
            metricService.setValue("arquivo_image_processor_blank_images_total", blankImagesTotal);
            metricService.setValue("arquivo_image_processor_response_items_incomplete_total", responseItemsIncompleteTotal);
            metricService.setValue("arquivo_image_processor_duplicate_files_total", duplicateFilesTotal);
            LOG.info("Total blank images: {}", blankImagesTotal);
            LOG.info("Total response items incomplete: {}", responseItemsIncompleteTotal);
            LOG.info("Total duplicate files skipped: {}", duplicateFilesTotal);
            LOG.info("Elapsed time: {} minutes", java.time.Duration.between(start, now).toMinutes());
            while (!now.isBefore(nextProgressLog)) {
                nextProgressLog = nextProgressLog.plusMinutes(SHOW_STATS_INTERVAL_MINS);
            }
        }
    }
}
