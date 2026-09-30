package com.assessment.auth.password;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Password reset token parameters (spec.md S7).
 *
 * @param tokenTtl 30 minutes, single use
 * @param tokenLength 32 alphanumeric characters from {@code SecureRandom} — roughly 190 bits, which
 *     is what justifies an unsalted SHA-256 rather than an adaptive hash (Std:66)
 */
@ConfigurationProperties(prefix = "app.reset")
public record ResetProperties(Duration tokenTtl, int tokenLength) {}
