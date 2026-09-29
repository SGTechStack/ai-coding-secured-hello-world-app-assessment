package sg.securedhello.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Password hashing and policy settings.
 *
 * @param bcryptStrength   the BCrypt cost; 12 in production (ADR-001)
 * @param minLength        the fewest code points a new password may have, after NFC; 15 (ADR-002)
 * @param maxBytes         the most UTF-8 bytes a new password may have, after NFC; 72 (ADR-003)
 * @param minStrengthScore the lowest zxcvbn score a new password may have; 3 (ADR-005)
 * @param historyLength    how many hashes are retained and refused for reuse, the current one included; 3 (REJ-007)
 */
@ConfigurationProperties("app.security.password")
public record PasswordProperties(int bcryptStrength, int minLength, int maxBytes, int minStrengthScore,
        int historyLength) {
}
