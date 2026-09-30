package com.example.hello.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.hello.support.MutableClock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class IpLoginThrottleTest {

  private final MutableClock clock = MutableClock.at("2026-03-01T10:00:00Z");
  private final IpLoginThrottle throttle = new IpLoginThrottle(3, Duration.ofMinutes(15), clock);

  @Test
  void throttlesAtThresholdWithinWindow() {
    throttle.recordFailure("1.1.1.1");
    throttle.recordFailure("1.1.1.1");
    assertThat(throttle.isThrottled("1.1.1.1")).isFalse();

    throttle.recordFailure("1.1.1.1");

    assertThat(throttle.isThrottled("1.1.1.1")).isTrue();
    assertThat(throttle.isThrottled("2.2.2.2")).as("other addresses unaffected").isFalse();
  }

  @Test
  void oldFailuresFallOutOfTheWindow() {
    throttle.recordFailure("1.1.1.1");
    throttle.recordFailure("1.1.1.1");
    clock.advance(Duration.ofMinutes(16));
    throttle.recordFailure("1.1.1.1");

    assertThat(throttle.isThrottled("1.1.1.1")).isFalse();
  }

  @Test
  void clearForgetsEverything() {
    throttle.recordFailure("1.1.1.1");
    throttle.recordFailure("1.1.1.1");
    throttle.recordFailure("1.1.1.1");

    throttle.clear();

    assertThat(throttle.isThrottled("1.1.1.1")).isFalse();
  }
}
