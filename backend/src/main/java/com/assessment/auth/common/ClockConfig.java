package com.assessment.auth.common;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One injectable {@link Clock}, used everywhere time is read (spec.md S1, S2).
 *
 * <p>UTC, because every stored timestamp is UTC even though logs render at {@code Asia/Singapore}.
 * Tests replace this bean with a fixed clock and advance it, which is how lockout cooldown, idle and
 * absolute session expiry and reset-token expiry are tested without sleeping (spec.md S12).
 */
@Configuration
public class ClockConfig {

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
