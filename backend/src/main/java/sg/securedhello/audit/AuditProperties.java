package sg.securedhello.audit;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Audit stream settings under {@code app.audit}. The audit file's directory, {@code app.audit.directory}, is read by
 * {@code logback-spring.xml} before this class binds.
 *
 * @param urlPath    the raw-URI cap on pre-handler rows
 * @param keying     the keying window both keyed tiers share (ADR-019; T-AUD-033)
 * @param truncation the distinct-key caps per keying window (ADR-019; REJ-080)
 */
@Validated
@ConfigurationProperties("app.audit")
public record AuditProperties(@Valid @DefaultValue UrlPath urlPath, @Valid @DefaultValue Keying keying,
        @Valid @DefaultValue Truncation truncation) {

    /** @param maxLength the longest raw {@code url.path}, marker included (REJ-081) */
    public record UrlPath(@DefaultValue("256") @Min(32) int maxLength) {
    }

    /** @param window the one window over which keyed rows aggregate and the caps reset */
    public record Keying(@DefaultValue("15m") @DurationMin(seconds = 1) Duration window) {
    }

    /**
     * Tunable, with a recomputation trigger: the daily audit volume is computed from them (REJ-080; R-AUD-030).
     *
     * @param distinctSources tier 1's cap: source keys tracked per window
     * @param distinctUsers   tier 2's cap: users tracked per window
     */
    public record Truncation(@DefaultValue("20") @Positive int distinctSources,
            @DefaultValue("500") @Positive int distinctUsers) {
    }
}
