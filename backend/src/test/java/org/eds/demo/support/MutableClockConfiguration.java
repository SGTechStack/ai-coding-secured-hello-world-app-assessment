package org.eds.demo.support;

import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces the application clock with a {@link MutableClock} that tests can control. */
@TestConfiguration
public class MutableClockConfiguration {

  @Bean
  @Primary
  MutableClock mutableClock() {
    return new MutableClock(Instant.parse("2030-01-01T00:00:00Z"));
  }
}
