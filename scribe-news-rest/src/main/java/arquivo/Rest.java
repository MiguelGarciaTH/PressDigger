package arquivo;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringBootApplication
@EntityScan("arquivo")
public class Rest {

    public static void main(String[] args) {
        SpringApplication.run(Rest.class, args);
    }

}

@Configuration
class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*") // Recommended way for Spring Boot 3+
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**");
    }
}

@Configuration
class ConfigJacksonConverter {
    @Bean
    MappingJackson2HttpMessageConverter jsonConverter() {


        // use the Jackson time serializers, but use custom serializers for LocalDateTime
        //final SimpleModule javaTimeSerializers = new JavaTimeModule();
        //javaTimeSerializers.addSerializer(LocalDateTime.class, new UtcLocalDateTimeSerializer());
        //javaTimeSerializers.addDeserializer(LocalDateTime.class, new UtcLocalDateTimeDeserializer());

        // set configuration flags, register AntPathFilterMixin and serializers modules
        final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder
                .json()
                .featuresToEnable(
                        JsonParser.Feature.ALLOW_COMMENTS)
                .featuresToDisable(
                        SerializationFeature.FAIL_ON_EMPTY_BEANS,
                        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                //.modulesToInstall(senseiSerializers, javaTimeSerializers)
                .build();

        return new MappingJackson2HttpMessageConverter(objectMapper);
    }
}
