package com.example.securedhello.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Log destinations, read by {@code logback-spring.xml} and validated here so a bad value fails
 * startup.
 *
 * @param applicationFile rolling JSON application-log file, the local buffer for a forwarding agent
 * @param auditFile rolling JSON audit-log file, separate from the application log
 * @param applicationMaxFileSize size at which the application-log file rolls over
 * @param emailFile rolling JSON file written by the {@code EmailService} stub, standing in for a
 *        mailbox
 * @param auditMaxHistoryDays days of audit-log history kept on disk; at least 90
 */
@Validated
@ConfigurationProperties("app.logging")
public record LoggingProperties(@NotBlank String applicationFile, @NotNull DataSize applicationMaxFileSize,
		@NotBlank String auditFile,
		@NotBlank String emailFile, @Min(value = 90, message = "app.logging.audit-max-history-days must keep at least 90 days") int auditMaxHistoryDays) {
}
