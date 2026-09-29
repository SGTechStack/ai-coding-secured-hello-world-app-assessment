package com.example.securedhello.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS configuration for the SPA. Allow-lists the configured frontend origin
 * and enables credentialed requests so the cookie-based Session and the CSRF
 * cookie work across origins. Exposes a {@link CorsConfigurationSource} bean so
 * the Spring Security filter chain applies the same policy.
 */
@Configuration
public class CorsConfig {

    private final String frontendOrigin;

    public CorsConfig(@Value("${app.cors.frontend-origin}") String frontendOrigin) {
        this.frontendOrigin = frontendOrigin;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(frontendOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
