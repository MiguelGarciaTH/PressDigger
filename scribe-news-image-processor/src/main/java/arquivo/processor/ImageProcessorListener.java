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

    @Value("${scribe-ref.arquivo.scribe-news-image-processor.http.read-timeout-ms:30000}")
    private int httpReadTimeoutMs;

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

        responseItemsReceivedTotal = metricService.loadValue("arquivo_image_processor_received_messages_total");
        blankImagesTotal = metricService.loadValue("arquivo_image_processor_blank_images_total");
        responseItemsIncompleteTotal = metricService.loadValue("arquivo_image_processor_response_items_incomplete_total");
        duplicateFilesTotal = metricService.loadValue("arquivo_image_processor_duplicate_files_total");
        noTextImageTotal = metricService.loadValue("arquivo_image_processor_no_text_images_total");
        responseItemsSentToKafkaTotal = metricService.loadValue("arquivo_image_processor_response_items_sent_to_kafka_total");

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
        responseItemsReceivedTotal++;
        metricService.updateValue("arquivo_image_processor_received_messages_total", 1);

        try {
            String payload = record.value();
            if (payload == null || payload.isBlank()) {
                LOG.warn("Empty payload for key {}", record.key());
                responseItemsIncompleteTotal++;
                metricService.updateValue("arquivo_image_processor_response_items_incomplete_total", 1);
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
                duplicateFilesTotal++;
                metricService.updateValue("arquivo_image_processor_duplicate_files_total", 1);
            } else {
                // process only if not duplicate
                final BufferedImage image = getImage(responseItem.get("linkToScreenshot").asText());
                if (image == null) {
                    // either blank or failed to fetch
                    LOG.warn("Skipping processing for title {} due to blank or fetch failure", responseItem.get("title").asText());
                    blankImagesTotal++;
                    metricService.updateValue("arquivo_image_processor_blank_images_total", 1);
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
            responseItemsSentToKafkaTotal++;
            metricService.updateValue("arquivo_image_processor_response_items_sent_to_kafka_total", 1);
            LOG.trace("Sent title {} to text summary topic", responseItem.get("title").asText());

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

    private BufferedImage getImage(String imageUrl) {
        final URI uri = URI.create(imageUrl);
        final URL url;
        try {
            url = uri.toURL();
        } catch (MalformedURLException e) {
            LOG.error("Invalid url {}", e.getMessage());
            responseItemsIncompleteTotal++;
            metricService.updateValue("arquivo_image_processor_response_items_incomplete_total", 1);
            return null;
        }

        BufferedImage image;
        try (InputStream in = openUrlStreamWithTimeouts(url)) {
            image = ImageIO.read(in);
        } catch (Exception e) {
            LOG.error("Failed to fetch image: {}", e.getMessage());
            responseItemsIncompleteTotal++;
            metricService.updateValue("arquivo_image_processor_response_items_incomplete_total", 1);
            return null;
        }

        if (image == null) {
            LOG.warn("ImageIO.read returned null for URL {}", imageUrl);
            responseItemsIncompleteTotal++;
            metricService.updateValue("arquivo_image_processor_response_items_incomplete_total", 1);
            return null;
        }

        // Check if truly blank (uniform color)
        if (ImageBlankDetector.isBlank(image, 30, 0.20, 5)) {
            blankImagesTotal++;
            metricService.updateValue("arquivo_image_processor_blank_images_total", 1);
            return null;
        }

        if (!imageTextDetector.hasText(image, 250)) {
            noTextImageTotal++;
            metricService.updateValue("arquivo_image_processor_no_text_images_total", 1);
            return null;
        }

        return image;
    }

    // helper to open URL input stream with configured timeouts
    private InputStream openUrlStreamWithTimeouts(URL url) throws IOException {
        URLConnection conn = url.openConnection();
        conn.setConnectTimeout(httpConnectTimeoutMs);
        conn.setReadTimeout(httpReadTimeoutMs);
        return conn.getInputStream();
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
