package sg.securedhello.security.ratelimit;

import java.time.Duration;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The lockout-cardinality axis, {@code app.security.rate-limit.lockout-cardinality} (ADR-015). It is not a row of the
 * budget table: a cardinality limit has no burst and no refill, and building it as a bucket would count events rather
 * than distinct accounts.
 *
 * @param k      the distinct accounts one source may drive into lockout per window (5)
 * @param window how long each account stays in its source's set, from its first lockout (1h)
 */
@Validated
@ConfigurationProperties("app.security.rate-limit.lockout-cardinality")
public record LockoutCardinalityProperties(@Positive int k, @NotNull @DurationMin(millis = 1) Duration window) {
}
