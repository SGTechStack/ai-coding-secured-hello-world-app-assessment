package com.example.securedhello.config;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Password reset settings.
 *
 * @param tokenExpiry how long a Reset Token is usable after issuance
 * @param frontendOrigin the SPA origin the reset link points to: {@code <frontendOrigin>/reset-password#token=<token>}
 */
@Validated
@ConfigurationProperties("app.password-reset")
public record PasswordResetProperties(@NotNull Duration tokenExpiry,
		@NotBlank @Pattern(regexp = "^https?://[^*/\\s]+$",
				message = "must be an exact origin without a path, query or fragment") String frontendOrigin) {
}
