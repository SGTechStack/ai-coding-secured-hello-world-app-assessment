package com.example.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Initial-admin bootstrap credentials, bound from app.admin.*.
 * The password must be supplied via configuration; there is no usable default
 * outside a dev profile (Story 51).
 */
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(String username, String email, String password) {
}
