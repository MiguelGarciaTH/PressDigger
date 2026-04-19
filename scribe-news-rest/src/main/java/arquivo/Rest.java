package arquivo;

import arquivo.model.User;
import arquivo.repository.UserRepository;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import arquivo.filter.RateLimitFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
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
    private final RateLimitFilter rateLimitFilter;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    public SecurityConfig(UserRepository userRepository, RateLimitFilter rateLimitFilter) {
        this.userRepository = userRepository;
        this.rateLimitFilter = rateLimitFilter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                frontendUrl,
                "https://press-digger.com",
                "https://www.press-digger.com"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "Authorization", "X-Requested-With"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers
                        .contentTypeOptions(opts -> {})       // X-Content-Type-Options: nosniff
                        .frameOptions(frame -> frame.deny())  // X-Frame-Options: DENY
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/collections/count-public").permitAll()
                        .requestMatchers("/collections/count-private").permitAll()
                        .requestMatchers("/collections/public/**").permitAll()
                        .requestMatchers("/collections/**").authenticated()
                        .requestMatchers("/annotations/**").authenticated()
                        .requestMatchers("/articles/narrative/**").authenticated()
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
    public FilterRegistrationBean<RateLimitFilter> disableAutoRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public AuthenticationSuccessHandler oAuth2SuccessHandler() {
        return (HttpServletRequest request, HttpServletResponse response, Authentication authentication) -> {
            OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

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

            response.sendRedirect(frontendUrl);
        };
    }
}

@Configuration
class WebConfig implements WebMvcConfigurer {

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