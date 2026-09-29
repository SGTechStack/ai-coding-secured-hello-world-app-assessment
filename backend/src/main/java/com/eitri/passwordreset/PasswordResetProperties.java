package com.eitri.passwordreset;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Reset token lifetime and the SPA origin that emailed links point at
 * ({@code <link-base-url>/reset-password?token=...}).
 */
@ConfigurationProperties("app.password-reset")
record PasswordResetProperties(Duration tokenLifetime, String linkBaseUrl) {

    PasswordResetProperties {
        Objects.requireNonNull(tokenLifetime, "app.password-reset.token-lifetime must be configured");
        if (tokenLifetime.isZero() || tokenLifetime.isNegative()) {
            throw new IllegalArgumentException("app.password-reset.token-lifetime must be positive");
        }
        if (linkBaseUrl == null || !linkBaseUrl.matches("https?://[^\\s?#]+")) {
            throw new IllegalArgumentException("app.password-reset.link-base-url must be an http(s) URL");
        }
        linkBaseUrl = linkBaseUrl.replaceAll("/+$", "");
    }
}
