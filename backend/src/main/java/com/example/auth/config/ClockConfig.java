package com.example.auth.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Single source of "now" so lockout cooldown and reset-token expiry are
 * controllable in tests (see docs/spec seams). Never call Instant.now()
 * directly in domain code — inject this Clock.
 */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
