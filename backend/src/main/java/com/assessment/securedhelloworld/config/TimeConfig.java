package com.assessment.securedhelloworld.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * The one production {@link Clock} adapter for the whole application.
 * Every module that makes a time-boxed decision (account lockout,
 * password-reset token expiry, dormancy sweep, access review expiry)
 * takes a {@code Clock} instead of calling {@code Instant.now()}
 * directly, so tests can substitute {@link Clock#fixed} deterministically
 * instead of hand-shifting timestamps relative to whatever "now" happens
 * to be when the test runs.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
