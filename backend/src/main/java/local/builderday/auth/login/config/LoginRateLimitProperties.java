package local.builderday.auth.login.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param attempts Login attempts allowed per source IP in each window, whatever their outcome
 * @param window fixed window after which the full allowance is restored at once
 */
@Validated
@ConfigurationProperties("app.security.login.rate-limit")
public record LoginRateLimitProperties(@Positive int attempts, @NotNull @DurationMin(nanos = 1) Duration window) {}
