package sg.securedhello.security.ratelimit;

import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Request limits under {@code app.security.request}.
 *
 * @param maxBodyBytes the largest request body accepted, in bytes: 16384 (Std §5:494; T-RL-011)
 */
@Validated
@ConfigurationProperties("app.security.request")
public record RequestBodyProperties(@Positive long maxBodyBytes) {
}
