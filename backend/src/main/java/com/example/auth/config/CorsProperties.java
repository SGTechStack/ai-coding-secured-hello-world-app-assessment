package com.example.auth.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Explicit allow-list of frontend origins permitted to send credentialed
 * (cookie) cross-origin requests. Bound from app.cors.* in application.yml.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
