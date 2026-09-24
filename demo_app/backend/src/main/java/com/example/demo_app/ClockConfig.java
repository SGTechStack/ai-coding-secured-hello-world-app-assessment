package com.example.demo_app;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The application's single source of "now". Inject this {@link Clock} wherever time matters
 * (lockout, throttling, token expiry, creation timestamps) instead of calling {@code
 * Instant.now()}, so tests can replace it with a controllable clock.
 */
@Configuration
class ClockConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
