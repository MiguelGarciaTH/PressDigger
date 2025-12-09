package arquivo.processor;

import arquivo.repository.RateLimiterRepository;
import arquivo.services.MetricService;
import arquivo.services.WebClientService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final ObjectMapper objectMapper;

    private final WebClientService webClientService;
    private final MetricService metricService;

    private long responseItemsCollectedTotal, responseItemsSentToKafkaTotal, responseItemsIncompleteTotal;

    @Autowired
    public ImageProcessorListener(RateLimiterRepository rateLimiterRepository,
                          MetricService metricService,
                          KafkaTemplate<String, String> kafkaTemplate) {
        this.metricService = metricService;
        this.kafkaTemplate = kafkaTemplate;
        this.webClientService = new WebClientService(rateLimiterRepository);
        this.objectMapper = new ObjectMapper();

        responseItemsCollectedTotal = metricService.loadValue("arquivo_crawler_response_items_collected_total");
        responseItemsSentToKafkaTotal = metricService.loadValue("arquivo_crawler_response_items_sent_to_kafka_total");
        responseItemsIncompleteTotal = metricService.loadValue("arquivo_crawler_response_items_incomplete_total");

    }

    @KafkaListener(
            topics = {"${scribe-ref.arquivo.scribe-news-image-processor.kafka.to-listen.topic}"},
            containerFactory = "kafkaListenerContainerFactory",
            concurrency = "${scribe-ref.arquivo.scribe-news-image-processor.kafka.to-listen.concurrency}")
    public void listener(ConsumerRecord<String, String> record, Acknowledgment ack, @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {
        LOG.info("Received on topic {} on partition {} record {}", record.topic(), partition, record.value());
    }


}
