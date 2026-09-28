package sg.securedhello.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The two HMAC keys: the tombstone email key (ADR-052) and the log correlation key (ADR-054). Presence only here; each
 * key's shape is checked where it is decoded, in {@link SecretsConfig} (ADR-062). No default in any profile.
 *
 * @param tombstone the forward-only versioned tombstone key
 * @param log       the log-field key
 */
@Validated
@ConfigurationProperties("app.security.hmac")
public record HmacProperties(@Valid @NotNull Tombstone tombstone, @Valid @NotNull Log log) {

    /**
     * @param key     padded Base64 of exactly 32 bytes
     * @param version the version new tombstones are written under
     */
    public record Tombstone(@NotBlank String key, @NotNull @Min(0) Integer version) {

        @Override
        public String toString() {
            return "Tombstone[key=<redacted>, version=" + version + "]";
        }
    }

    /** @param key padded Base64 of exactly 32 bytes */
    public record Log(@NotBlank String key) {

        @Override
        public String toString() {
            return "Log[key=<redacted>]";
        }
    }
}
