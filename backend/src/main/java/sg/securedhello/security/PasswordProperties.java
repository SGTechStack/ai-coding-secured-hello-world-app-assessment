package sg.securedhello.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Password hashing settings.
 *
 * @param bcryptStrength the BCrypt cost; 12 in production (ADR-001)
 */
@ConfigurationProperties("app.security.password")
public record PasswordProperties(int bcryptStrength) {
}
