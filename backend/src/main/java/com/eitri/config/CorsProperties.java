package com.eitri.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Frontend origins allowed to call the API cross-origin with credentials. Empty by default, so no
 * origin is allowed unless a deployment lists it explicitly.
 */
@ConfigurationProperties("app.security.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        for (String origin : allowedOrigins) {
            if (origin == null || origin.isBlank() || origin.contains("*")) {
                throw new IllegalArgumentException(
                        "app.security.cors.allowed-origins must list explicit origins without wildcards");
            }
        }
    }
}
