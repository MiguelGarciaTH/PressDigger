package arquivo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication
@EntityScan("arquivo")
public class Crawler {

	public static void main(String[] args) {
		SpringApplication.run(Crawler.class, args);
	}

}
