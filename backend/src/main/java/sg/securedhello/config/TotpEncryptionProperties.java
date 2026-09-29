package sg.securedhello.config;

import java.util.Map;

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
 * <p>Yearly rotation runs row by row (ADR-022; R-CFG-005): the operator mints a new key and version, and moves the old
 * key under {@code retired-keys.<its version>}, so rows still sealed under it keep opening until each is re-sealed.
 * Optional and empty by default; each retired key is checked like the current one. The version is bound as text and
 * range-checked in {@link SecretsConfig}: a constraint on a map key would report the bound key as the rejected value.
 *
 * @param key         padded Base64 of exactly 32 bytes
 * @param keyVersion  the current key version, stored beside every ciphertext (0..255)
 * @param retiredKeys earlier keys by the version (0..255) they were current under, for rows not yet re-sealed
 */
@Validated
@ConfigurationProperties("app.mfa.totp.encryption")
public record TotpEncryptionProperties(@NotBlank String key, @NotNull @Min(0) @Max(255) Integer keyVersion,
        Map<String, @NotBlank String> retiredKeys) {

    public TotpEncryptionProperties {
        retiredKeys = retiredKeys == null ? Map.of() : Map.copyOf(retiredKeys);
    }

    @Override
    public String toString() {
        return "TotpEncryptionProperties[key=<redacted>, keyVersion=" + keyVersion + ", retiredKeyVersions="
                + retiredKeys.keySet() + "]";
    }
}
