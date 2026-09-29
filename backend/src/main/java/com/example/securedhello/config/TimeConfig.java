package com.example.securedhello.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a single injectable {@link Clock} time source. All time-dependent
 * behaviour (lockout cooldowns, token expiry) reads time through this bean so
 * tests can substitute a fixed or offset clock deterministically.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
