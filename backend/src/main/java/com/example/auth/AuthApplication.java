package com.example.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Bootstrap entry point for the auth backend.
 *
 * <p>The Spring Boot backend exposes the auth API under {@code /api/**} and
 * is deployed as a separate origin from the React SPA; see
 * {@link com.example.auth.security.SecurityConfig} for the CORS
 * configuration that permits the frontend's cross-origin requests.
 *
 * <p>{@code @ConfigurationPropertiesScan} picks up {@link
 * com.example.auth.user.LocalUsersProperties}, the only {@code
 * @ConfigurationProperties} class in this codebase so far -- everything else
 * uses {@code @Value}.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
