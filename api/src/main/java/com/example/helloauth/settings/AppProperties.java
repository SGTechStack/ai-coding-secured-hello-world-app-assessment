package com.example.helloauth.settings;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Application settings. Every security-relevant threshold is here rather than inline, so that the
 * policy is readable in one place and adjustable per environment.
 */
@ConfigurationProperties(prefix = "app")
@Validated
public record AppProperties(
        @Valid @NotNull Cors cors,
        @NotBlank String frontendBaseUrl,
        @Valid @NotNull Admin admin,
        @Valid @NotNull PasswordPolicy passwordPolicy,
        @Valid @NotNull Lockout lockout,
        @Valid @NotNull Throttle throttle,
        @Valid @NotNull PasswordReset passwordReset) {

    /**
     * @param allowedOrigins explicit allow-list of frontend origins. A wildcard is not an option:
     *     browsers refuse {@code Access-Control-Allow-Origin: *} on credentialed requests, and the
     *     session cookie makes every request credentialed.
     */
    public record Cors(@NotEmpty List<String> allowedOrigins) {}

    /**
     * Credentials for the first admin account.
     *
     * @param password blank means "do not seed" — an unset password must never become a guessable
     *     admin account.
     */
    public record Admin(@NotBlank String username, @NotBlank String email, String password) {}

    /**
     * @param minLength the PRD's entire stated policy
     * @param maxLength BCrypt ignores input past 72 bytes, so longer passwords would be silently
     *     truncated and two different secrets could open one account
     */
    public record PasswordPolicy(@Min(1) int minLength, @Min(1) int maxLength) {}

    /**
     * @param maxFailedAttempts consecutive failures that trigger a lockout
     * @param cooldown how long the lockout lasts before lifting itself
     */
    public record Lockout(@Min(1) int maxFailedAttempts, @NotNull Duration cooldown) {}

    /**
     * Per-IP limits, deliberately independent of per-account lockout.
     *
     * @param maxFailures attempts tolerated within the window before requests are refused
     * @param window rolling window the failures are counted over
     */
    public record Throttle(@Min(1) int maxFailures, @NotNull Duration window) {}

    /**
     * @param tokenTtl how long a reset token stays redeemable; the PRD asks for 15–30 minutes
     */
    public record PasswordReset(@NotNull Duration tokenTtl) {}
}
