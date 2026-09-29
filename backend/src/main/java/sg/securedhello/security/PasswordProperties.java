package sg.securedhello.security;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Password hashing and policy settings. Binding refuses startup on a value that weakens the ADRs or that would fail
 * only at runtime: a history of 0 breaks every password set, and more than 72 bytes reaches BCrypt, which throws.
 *
 * @param bcryptStrength   the BCrypt cost; 12 in production (ADR-001). Bound here to BCrypt's own range, 4 to 31: the
 *                         shared test contexts run at 4 under {@code dev}, and outside {@code dev}
 *                         {@link PasswordEncoderConfig} refuses anything below 12
 * @param minLength        the fewest code points a new password may have, after NFC; 15 (ADR-002), never fewer
 * @param maxBytes         the most UTF-8 bytes a new password may have, after NFC; 72 (ADR-003), BCrypt's limit
 * @param minStrengthScore the lowest zxcvbn score a new password may have; 3 (ADR-005), or 4, zxcvbn's highest
 * @param historyLength    how many hashes are retained and refused for reuse, the current one included; 3 (REJ-007),
 *                         at least 1
 */
@Validated
@ConfigurationProperties("app.security.password")
public record PasswordProperties(@Min(4) @Max(31) int bcryptStrength, @Min(15) int minLength,
        @Min(1) @Max(72) int maxBytes, @Min(3) @Max(4) int minStrengthScore, @Min(1) int historyLength) {

    /** A byte ceiling below the minimum length admits no password at all: every code point is at least one byte. */
    @AssertTrue(message = "max-bytes must be at least min-length")
    public boolean isMaxBytesAtLeastMinLength() {
        return maxBytes >= minLength;
    }
}
