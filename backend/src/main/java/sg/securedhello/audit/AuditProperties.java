package sg.securedhello.audit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Audit stream settings under {@code app.audit}. The audit file's directory, {@code app.audit.directory}, is read by
 * {@code logback-spring.xml} before this class binds.
 *
 * @param urlPath the raw-URI cap on pre-handler rows
 */
@Validated
@ConfigurationProperties("app.audit")
public record AuditProperties(@Valid @DefaultValue UrlPath urlPath) {

    /** @param maxLength the longest raw {@code url.path}, marker included (REJ-081) */
    public record UrlPath(@DefaultValue("256") @Min(32) int maxLength) {
    }
}
