package sg.example.helloauth.audit;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param ipHashSecret the key for the HMAC-SHA256 that stands in for client IPs in audit events.
 *        The audit log file's location, {@code app.audit.log-file}, is read by logback-spring.xml.
 */
@ConfigurationProperties("app.audit")
@Validated
record AuditProperties(@NotNull @Size(min = 32) String ipHashSecret) {
}
