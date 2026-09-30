package local.builderday.account.passwordreset.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param tokenLifetime how long a Password reset token works after it is issued
 * @param requestRateLimit reset requests per source IP, whatever their outcome; over it the client is told
 * @param confirmRateLimit confirm attempts per source IP, whatever their outcome; over it the client is told
 * @param emailCap reset emails per account, whoever asks; silent, so it reveals nothing about the account
 */
@Validated
@ConfigurationProperties("app.security.password-reset")
public record PasswordResetProperties(@NotNull @DurationMin(nanos = 1) Duration tokenLifetime,
    @NotNull @Valid Limit requestRateLimit, @NotNull @Valid Limit confirmRateLimit, @NotNull @Valid Limit emailCap) {

  /** A fixed window: {@code attempts} per {@code window}, restored in full when the window ends. */
  public record Limit(@Positive int attempts, @NotNull @DurationMin(nanos = 1) Duration window) {}
}
