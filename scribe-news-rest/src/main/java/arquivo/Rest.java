package arquivo;

import arquivo.model.User;
import arquivo.repository.UserRepository;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Map;

@SpringBootApplication
@EntityScan("arquivo")
public class Rest {

    public static void main(String[] args) {
        SpringApplication.run(Rest.class, args);
    }

}

@Configuration
class SecurityConfig {

    private final UserRepository userRepository;

    public SecurityConfig(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> {
                })
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/collections/public/**").permitAll()
                        .requestMatchers("/collections/**").authenticated()
                        .requestMatchers("/annotations/**").authenticated()
                        .anyRequest().permitAll()
                )
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(oAuth2SuccessHandler())
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .maximumSessions(1)
                );
        return http.build();
    }

    @Bean
    public AuthenticationSuccessHandler oAuth2SuccessHandler() {
        return (HttpServletRequest request, HttpServletResponse response, Authentication authentication) -> {
            OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

            // Store user info in session (same format as your GSI flow)
            Map<String, Object> user = Map.of(
                    "googleId", oAuth2User.getAttribute("sub"),
                    "email", oAuth2User.getAttribute("email"),
                    "name", oAuth2User.getAttribute("name"),
                    "picture", oAuth2User.getAttribute("picture")
            );
            request.getSession().setAttribute("user", user);

            if (userRepository.findByGoogleId(oAuth2User.getAttribute("sub")).isEmpty()) {
                userRepository.save(new User(oAuth2User.getAttribute("name"), oAuth2User.getAttribute("email"), oAuth2User.getAttribute("sub")));
            }

            // Redirect to frontend
            response.sendRedirect("http://localhost:5173");
        };
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

    }
}


@Configuration
class ConfigJacksonConverter {
    @Bean
    MappingJackson2HttpMessageConverter jsonConverter() {

        final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder
                .json()
                .featuresToEnable(
                        JsonParser.Feature.ALLOW_COMMENTS)
                .featuresToDisable(
                        SerializationFeature.FAIL_ON_EMPTY_BEANS,
                        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();

        return new MappingJackson2HttpMessageConverter(objectMapper);
    }
}
