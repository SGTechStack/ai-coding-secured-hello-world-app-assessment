package sg.securedhello.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The TOTP seed encryption key (ADR-022). Presence only here; the key's shape is checked where it is decoded, in
 * {@link SecretsConfig}, so a failure report never echoes it (ADR-062). No default in any profile.
 *
 * @param key        padded Base64 of exactly 32 bytes
 * @param keyVersion the current key version, stored beside every ciphertext (0..255)
 */
@Validated
@ConfigurationProperties("app.mfa.totp.encryption")
public record TotpEncryptionProperties(@NotBlank String key, @NotNull @Min(0) @Max(255) Integer keyVersion) {

    @Override
    public String toString() {
        return "TotpEncryptionProperties[key=<redacted>, keyVersion=" + keyVersion + "]";
    }
}
