package arquivo.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

public class KafkaPublisher {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaPublisher.class);


    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final ObjectMapper objectMapper;

    public KafkaPublisher(KafkaTemplate<String, String> kafkaTemplate, String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.objectMapper = new ObjectMapper();
    }

    public void send(JsonNode responseItem) {
        try {
            kafkaTemplate.send(topic, objectMapper.writeValueAsString(responseItem));
            LOG.debug("Sent to topic {} and partition value={}", topic, responseItem);
        } catch (JsonProcessingException e) {
            LOG.error("Error processing item: {}", responseItem.toPrettyString(), e);
        }
    }
}
