package com.example.demo_app.user;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.security.lockout}: how many consecutive failed logins lock an account, and for how
 * long. The defaults live in {@code application.yml}. A missing or non-positive value stops
 * startup, so lockout can never be silently off.
 */
@ConfigurationProperties("app.security.lockout")
public record LockoutProperties(int maxFailures, Duration duration) {

  public LockoutProperties {
    if (maxFailures < 1 || duration == null || duration.isNegative() || duration.isZero()) {
      throw new IllegalArgumentException(
          "app.security.lockout needs a positive max-failures and duration, got "
              + maxFailures
              + " and "
              + duration);
    }
  }
}
