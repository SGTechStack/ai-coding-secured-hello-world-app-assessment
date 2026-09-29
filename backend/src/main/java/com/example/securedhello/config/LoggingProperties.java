package com.example.securedhello.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Log destinations, read by {@code logback-spring.xml} and validated here so a bad value fails
 * startup.
 *
 * @param applicationFile rolling JSON application-log file, the local buffer for a forwarding agent
 * @param auditFile rolling JSON audit-log file, separate from the application log
 * @param auditMaxHistoryDays days of audit-log history kept on disk; at least 90
 */
@Validated
@ConfigurationProperties("app.logging")
public record LoggingProperties(@NotBlank String applicationFile, @NotBlank String auditFile,
		@Min(value = 90, message = "app.logging.audit-max-history-days must keep at least 90 days") int auditMaxHistoryDays) {
}
