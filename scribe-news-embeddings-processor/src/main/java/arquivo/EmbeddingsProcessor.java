package arquivo;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.FixedBackOff;

@SpringBootApplication
@EntityScan("arquivo")
public class EmbeddingsProcessor {

    public static void main(String[] args) {
        SpringApplication.run(EmbeddingsProcessor.class, args);
    }

    /**
     * Configure Kafka listener
     */
    @Configuration
    class ConfigKafkaListener {
        @Bean
        ConcurrentKafkaListenerContainerFactory<?, ?> kafkaListenerContainerFactory(KafkaProperties kafkaProperties,
                                                                                    ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
                                                                                    ConsumerFactory<Object, Object> kafkaConsumerFactory) {
            final ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
            configurer.configure(factory, kafkaConsumerFactory);
            factory.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(0L, 2L)));
            factory.setConcurrency(kafkaProperties.getListener().getConcurrency());
            return factory;
        }
    }

    @Configuration
    @Component
    class TopicConfig {

        @Value("${scribe-ref.arquivo.scribe-news-embeddings-processor.kafka.to-listen.topic}")
        private String topicToListen;

        @Value("${scribe-ref.arquivo.scribe-news-embeddings-processor.kafka.to-listen.concurrency}")
        private int concurrencyToListen;

        @Bean
        public NewTopic createTopic() {
            return TopicBuilder.name(topicToListen)
                    .partitions(concurrencyToListen)
                    .build();
        }
    }
}
