package local.builderday.account.registration.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Environment-configurable registration abuse-control policy; defaults live in {@code application.yml}. State
 * retention and cleanup belong to the shared store ({@code app.security.rate-limit}).
 *
 * @param rejectedAttemptThreshold rejected server-processed submissions per source IP that trigger a lock
 * @param window rolling window the threshold applies to
 * @param lockDuration how long a locked source IP cannot register
 */
@Validated
@ConfigurationProperties("app.security.registration.rate-limit")
public record RegistrationRateLimitProperties(@Positive int rejectedAttemptThreshold,
    @NotNull @DurationMin(nanos = 1) Duration window, @NotNull @DurationMin(nanos = 1) Duration lockDuration) {}
