package arquivo.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service

public class ImageTextDetector {

    private static final Logger LOG = LoggerFactory.getLogger(ImageTextDetector.class);

    @Value("${scribe-ref.arquivo.scribe-news-image-processor.ocr-service-url}")
    private String ocrServiceUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public ImageTextDetector() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Check if image contains text using OCR service.
     *
     * @param image    BufferedImage to check
     * @param minWords minimum number of words required
     * @return true if image has at least minWords
     */
    public boolean hasText(BufferedImage image, int minWords) {
        try {
            // Convert BufferedImage to byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            byte[] imageBytes = baos.toByteArray();

            // Prepare multipart request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(imageBytes) {
                @Override
                public String getFilename() {
                    return "image.png";
                }
            });

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            // Call OCR service
            String url = ocrServiceUrl + "/detect-text?min_words=" + minWords;
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode result = objectMapper.readTree(response.getBody());
                boolean hasText = result.get("has_text").asBoolean();
                int wordCount = result.get("word_count").asInt();
                double confidence = result.get("confidence").asDouble();

                LOG.debug("OCR result: hasText={}, wordCount={}, confidence={}",
                        hasText, wordCount, confidence);

                return hasText;
            } else {
                LOG.warn("OCR service returned error: {}", response.getStatusCode());
                return false;
            }

        } catch (IOException e) {
            LOG.error("Failed to convert image to bytes: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            LOG.error("OCR service call failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if OCR service is available.
     */
    public boolean isAvailable() {
        try {
            String url = ocrServiceUrl + "/health";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            LOG.warn("OCR service not available: {}", e.getMessage());
            return false;
        }
    }

}
