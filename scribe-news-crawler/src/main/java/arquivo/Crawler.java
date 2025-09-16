package arquivo;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.stereotype.Component;

@SpringBootApplication
@EntityScan("arquivo")
public class Crawler {

	public static void main(String[] args) {
		SpringApplication.run(Crawler.class, args);
	}

}

@Configuration
@Component
class TopicConfig {

	@Value("${image-crop.topic}")
	private String topic;

	@Bean
	public NewTopic createTopic() {
		return TopicBuilder.name(topic)
				.partitions(15)
				.build();
	}
}