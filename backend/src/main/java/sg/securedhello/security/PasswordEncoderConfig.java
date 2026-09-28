package sg.securedhello.security;

import java.util.Map;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** BCrypt behind {@link DelegatingPasswordEncoder}, so every hash records its scheme and cost (ADR-001). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PasswordProperties.class)
public class PasswordEncoderConfig {

    private static final String BCRYPT = "bcrypt";

    @Bean
    PasswordEncoder passwordEncoder(PasswordProperties properties) {
        return new DelegatingPasswordEncoder(BCRYPT,
                Map.of(BCRYPT, new BCryptPasswordEncoder(properties.bcryptStrength())));
    }
}
