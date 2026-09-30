package com.assessment.auth.support;

import java.time.Clock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Supplies {@link MutableClock} as the {@link Clock} every component receives.
 *
 * <p><strong>{@code @Primary} under a different bean name, not a same-name override.</strong> Naming
 * it {@code clock} to shadow {@code ClockConfig#clock} does not work: a {@code @TestConfiguration}
 * imported into the test is registered after the application's own configuration, so the
 * application's definition wins and every test silently gets the real system clock. That failure mode
 * is quiet — the clock injects fine, {@code advance()} has no effect, and the lockout-cooldown and
 * token-expiry tests fail with assertions that look like application defects.
 */
@TestConfiguration
public class TestClockConfig {

  @Bean
  @Primary
  public Clock testClock() {
    return new MutableClock();
  }
}
