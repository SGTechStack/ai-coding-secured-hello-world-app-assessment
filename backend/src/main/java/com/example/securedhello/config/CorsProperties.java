package com.example.securedhello.config;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Frontend origins allowed to call the API with credentials. There is no default outside the
 * {@code dev} profile, so startup fails until an allowlist is configured. Wildcards are rejected.
 *
 * @param allowedOrigins exact origins such as {@code https://app.example.gov}
 */
@Validated
@ConfigurationProperties("app.cors")
public record CorsProperties(
		@NotEmpty(message = "app.cors.allowed-origins must list the frontend origins") List<
				@Pattern(regexp = "^https?://[^*/\\s]+$", message = "must be an exact origin without wildcards or paths") String> allowedOrigins) {
}
