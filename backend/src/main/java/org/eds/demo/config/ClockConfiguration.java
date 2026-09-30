package org.eds.demo.config;

import java.time.Clock;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;

/**
 * Single source of time for the application, so tests can control it.
 *
 * <p>Persisted audit timestamps come from this clock rather than the system clock, and
 * time-dependent behavior such as sign-in backoff should inject it too. Spring Session keeps its
 * own session times on the system clock.
 */
@Configuration
class ClockConfiguration {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  DateTimeProvider auditingDateTimeProvider(Clock clock) {
    return () -> Optional.of(clock.instant());
  }
}
