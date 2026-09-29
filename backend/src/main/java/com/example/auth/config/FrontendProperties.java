package com.example.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Frontend origin used to build user-facing links (e.g. password-reset). */
@ConfigurationProperties(prefix = "app.frontend")
public record FrontendProperties(String baseUrl) {
}
