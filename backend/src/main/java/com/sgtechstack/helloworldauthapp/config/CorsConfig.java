package com.sgtechstack.helloworldauthapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS is configured with an explicit allow-list of the frontend origin(s),
 * never a wildcard, because credentials (the session cookie) must be allowed
 * to travel cross-origin.
 *
 * This is exposed as a {@link CorsConfigurationSource} bean, consumed
 * directly by Spring Security's {@code HttpSecurity.cors(...)}, rather than
 * via a {@code WebMvcConfigurer}. Spring Security's filter chain sits in
 * front of Spring MVC dispatch, so endpoints handled entirely by security
 * filters (form login, logout) never reach MVC-level CORS configuration —
 * only the security-level CORS filter sees them.
 */
@Configuration
public class CorsConfig {

    private final List<String> allowedOrigins;

    public CorsConfig(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
