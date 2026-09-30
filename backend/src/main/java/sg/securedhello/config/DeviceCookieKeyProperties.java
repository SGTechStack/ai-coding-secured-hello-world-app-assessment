package sg.securedhello.config;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The device-cookie HMAC key (ADR-075): the secret that signs each trusted device's cookie. Presence only here; its
 * shape is checked where it is decoded, in {@link SecretsConfig} (ADR-062). No default in the base configuration; only
 * {@code application-dev.yml} carries a published value, which {@link PublishedDemoValues} refuses outside {@code dev}.
 * The device lane's other settings bind in {@code LockoutProperties}.
 *
 * @param secret padded Base64 of exactly 32 bytes
 */
@Validated
@ConfigurationProperties("app.security.lockout.device")
public record DeviceCookieKeyProperties(@NotBlank String secret) {

    @Override
    public String toString() {
        return "DeviceCookieKeyProperties[secret=<redacted>]";
    }
}
