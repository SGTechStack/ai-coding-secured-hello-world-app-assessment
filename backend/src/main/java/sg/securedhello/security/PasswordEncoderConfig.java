package sg.securedhello.security;

import java.util.Map;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * BCrypt behind {@link DelegatingPasswordEncoder}, so every hash records its scheme and cost (ADR-001).
 *
 * <p>Outside {@code dev} the context refresh fails on a cost below {@value #PRODUCTION_FLOOR}, checked on the bound
 * value, so every spelling Boot converts is covered. Under {@code dev} the shared test contexts hash at cost 4 for
 * speed; {@link PasswordProperties} bounds the cost to BCrypt's own range in every posture. Never lazy, so the
 * refusal comes before the port opens even under {@code spring.main.lazy-initialization}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PasswordProperties.class)
public class PasswordEncoderConfig {

    /** The production BCrypt cost floor (ADR-001). */
    static final int PRODUCTION_FLOOR = 12;

    private static final String BCRYPT = "bcrypt";

    @Bean
    @Lazy(false)
    PasswordEncoder passwordEncoder(PasswordProperties properties, Environment environment) {
        if (properties.bcryptStrength() < PRODUCTION_FLOOR && !environment.matchesProfiles("dev")) {
            throw new IllegalStateException("Startup refused: app.security.password.bcrypt-strength is below "
                    + PRODUCTION_FLOOR + " outside the dev profile (ADR-001)");
        }
        return new DelegatingPasswordEncoder(BCRYPT,
                Map.of(BCRYPT, new BCryptPasswordEncoder(properties.bcryptStrength())));
    }
}
