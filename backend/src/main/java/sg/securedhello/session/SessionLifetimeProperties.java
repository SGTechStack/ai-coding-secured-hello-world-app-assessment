package sg.securedhello.session;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Session limits beyond the idle window, which is {@code spring.session.timeout}.
 *
 * @param absolute how long a signed-in session lives from its {@code AUTH_INSTANT}, whatever its traffic; 8 hours
 *                 (REJ-012; ADR-038)
 */
@ConfigurationProperties("app.security.session")
public record SessionLifetimeProperties(@DefaultValue("8h") Duration absolute) {
}
