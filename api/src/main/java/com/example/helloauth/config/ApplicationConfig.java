package com.example.helloauth.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class ApplicationConfig {

    /**
     * Injected rather than called statically, so that lockout and token expiry are testable
     * without sleeping through real cooldowns.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** BCrypt, as the PRD requires. No custom hashing, no alternative algorithm. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
