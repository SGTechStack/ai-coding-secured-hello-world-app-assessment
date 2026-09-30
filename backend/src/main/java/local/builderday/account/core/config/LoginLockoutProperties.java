package local.builderday.account.core.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param threshold consecutive failed Login attempts against one account that set a Login lockout
 * @param duration fixed length of the lock; attempts during it never extend it
 */
@Validated
@ConfigurationProperties("app.security.login.lockout")
public record LoginLockoutProperties(@Positive int threshold, @NotNull @DurationMin(nanos = 1) Duration duration) {}
