package com.example.demo_app.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.security.throttle}: per-IP limits for the endpoints an attacker would flood. Each
 * endpoint gets its own {@link Limit} and its own {@link IpThrottle}; the defaults live in {@code
 * application.yml}. A missing or non-positive value stops startup, so a throttle can never be
 * silently off.
 *
 * @param maxTrackedIps how many client addresses each throttle remembers before forgetting the
 *     oldest, which bounds its memory
 * @param login failed logins allowed per IP per window
 * @param registration registration requests (of any outcome) allowed per IP per window
 * @param passwordResetRequest password reset requests (of any outcome) allowed per IP per window
 */
@ConfigurationProperties("app.security.throttle")
public record ThrottleProperties(
    int maxTrackedIps, Limit login, Limit registration, Limit passwordResetRequest) {

  public ThrottleProperties {
    if (maxTrackedIps < 1) {
      throw new IllegalArgumentException("app.security.throttle.max-tracked-ips must be positive");
    }
    requirePresent(login, "login");
    requirePresent(registration, "registration");
    requirePresent(passwordResetRequest, "password-reset-request");
  }

  private static void requirePresent(Limit limit, String name) {
    if (limit == null) {
      throw new IllegalArgumentException(
          "app.security.throttle." + name + " must set max-attempts and window");
    }
  }

  /**
   * At most {@code maxAttempts} counted attempts per IP in a fixed {@code window}, which starts at
   * the first counted attempt.
   */
  public record Limit(int maxAttempts, Duration window) {

    public Limit {
      if (maxAttempts < 1 || window == null || window.isNegative() || window.isZero()) {
        throw new IllegalArgumentException(
            "a throttle limit needs a positive max-attempts and window, got "
                + maxAttempts
                + " per "
                + window);
      }
    }
  }
}
