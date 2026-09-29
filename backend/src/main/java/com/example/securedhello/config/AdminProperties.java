package com.example.securedhello.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the bootstrap Admin account seeded on first startup when
 * no ADMIN exists. Supplied via {@code app.admin.username} / {@code
 * app.admin.password}.
 */
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(String username, String password) {
}
