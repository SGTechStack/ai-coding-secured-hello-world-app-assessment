package sg.securedhello.mfa;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * TOTP settings that are not secret. The issuer names the service in an authenticator app, in the {@code otpauth}
 * URI's label and {@code issuer} parameter. Required: blank refuses startup (T-CFG-023), and it is registered with
 * {@code setRequiredProperties}, so an unresolved placeholder does too (T-CFG-038).
 *
 * @param issuer the service name an authenticator app shows beside the account
 */
@Validated
@ConfigurationProperties("app.mfa.totp")
public record TotpProperties(@NotBlank String issuer) {
}
