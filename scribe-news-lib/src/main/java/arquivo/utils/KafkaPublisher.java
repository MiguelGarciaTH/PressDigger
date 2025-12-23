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
    private int roundRobinIndex = 0;
    private final String topic;
    private final int concurrency;
    private final ObjectMapper objectMapper;

    public KafkaPublisher(KafkaTemplate<String, String> kafkaTemplate, String topic, int concurrency) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.concurrency = concurrency;
        this.objectMapper = new ObjectMapper();
    }


    public void send(JsonNode responseItem) {
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
}
