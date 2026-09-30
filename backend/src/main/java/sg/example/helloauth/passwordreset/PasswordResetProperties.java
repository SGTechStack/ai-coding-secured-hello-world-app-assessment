package sg.example.helloauth.passwordreset;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * @param resetPageUrl the SPA's reset-password page. The emailed link is this URL with the
 *        Password reset token in its {@code token} query parameter.
 * @param tokenValidity how long a Password reset token works after it is issued
 */
@ConfigurationProperties("app.password-reset")
@Validated
record PasswordResetProperties(@NotBlank String resetPageUrl, @DefaultValue("30m") Duration tokenValidity) {
}
