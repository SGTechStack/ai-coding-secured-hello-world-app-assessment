package local.builderday.common.ratelimit;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Shared rate-limit store settings. The cleanup schedule is {@code app.security.rate-limit.cleanup-cron}, read
 * directly by the scheduler.
 *
 * @param retention how long bucket state is kept after its bucket is full again, before cleanup deletes it
 */
@Validated
@ConfigurationProperties("app.security.rate-limit")
public record RateLimitProperties(@NotNull @DurationMin(nanos = 1) Duration retention) {}
