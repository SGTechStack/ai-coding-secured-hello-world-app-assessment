package com.example.hello;

import static org.assertj.core.api.Assertions.*;

import com.example.hello.auth.LoginThrottle;
import com.example.hello.auth.ThrottledException;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LoginThrottleTest {
  @Test
  void concurrentFailuresStopCredentialChecksAfterThreeAttempts() throws Exception {
    LoginThrottle throttle = new LoginThrottle(Clock.systemUTC());
    var checks = new java.util.concurrent.atomic.AtomicInteger();
    var start = new java.util.concurrent.CountDownLatch(1);
    try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      var futures = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
      for (int i = 0; i < 10; i++) {
        futures.add(
            executor.submit(
                () -> {
                  start.await();
                  try {
                    throttle.attempt(
                        "one-source",
                        () -> {
                          checks.incrementAndGet();
                          return Optional.empty();
                        });
                    return true;
                  } catch (ThrottledException ignored) {
                    return false;
                  }
                }));
      }
      start.countDown();
      int accepted = 0;
      for (var future : futures)
        if (future.get(10, java.util.concurrent.TimeUnit.SECONDS)) accepted++;
      assertThat(accepted).isEqualTo(3);
      assertThat(checks.get()).isEqualTo(3);
    }
  }

  @Test
  void failuresNearBucketBoundaryCannotBypassTheAccountProtection() {
    MutableClock clock = new MutableClock();
    LoginThrottle throttle = new LoginThrottle(clock);
    throttle.attempt("one-source", () -> Optional.of("valid-login"));
    clock.seconds = 899;
    for (int i = 0; i < 3; i++) throttle.attempt("one-source", Optional::empty);
    clock.seconds = 901;
    assertThatThrownBy(() -> throttle.attempt("one-source", Optional::empty))
        .isInstanceOf(ThrottledException.class);
    assertThat(throttle.attempt("different-source", () -> Optional.of("valid-login")))
        .contains("valid-login");
    clock.seconds = 1800;
    assertThat(throttle.attempt("one-source", () -> Optional.of("valid-login")))
        .contains("valid-login");
  }

  private static class MutableClock extends Clock {
    long seconds;

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return Instant.EPOCH.plusSeconds(seconds);
    }
  }
}
