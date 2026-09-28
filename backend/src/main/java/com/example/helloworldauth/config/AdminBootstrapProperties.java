package com.example.helloworldauth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bootstrap admin credentials read from {@code app.admin.*}. Used once on
 * startup by {@link AdminBootstrap} to seed the first ADMIN account when none
 * exists. The password is never stored or logged in plaintext.
 */
@ConfigurationProperties(prefix = "app.admin")
public record AdminBootstrapProperties(String username, String email, String password) {

    public boolean hasPassword() {
        return password != null && !password.isBlank();
    }
}
