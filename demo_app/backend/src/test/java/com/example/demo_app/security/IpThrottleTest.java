package com.example.demo_app.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo_app.MutableClock;
import com.example.demo_app.web.TooManyRequestsException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** The limiter's window, eviction and {@code Retry-After} arithmetic, without Spring. */
class IpThrottleTest {

  private static final Duration WINDOW = Duration.ofMinutes(15);

  private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));

  private IpThrottle throttle(int maxAttempts, int maxTrackedIps) {
    return new IpThrottle(new ThrottleProperties.Limit(maxAttempts, WINDOW), maxTrackedIps, clock);
  }

  private static MockHttpServletRequest from(String ip) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr(ip);
    return request;
  }

  private static Duration retryAfter(IpThrottle throttle, MockHttpServletRequest request) {
    try {
      throttle.check(request);
    } catch (TooManyRequestsException e) {
      return e.retryAfter();
    }
    throw new AssertionError("expected " + request.getRemoteAddr() + " to be throttled");
  }

  @Test
  void allowsUpToTheLimitThenThrottlesWithTheRestOfTheWindow() {
    IpThrottle throttle = throttle(3, 10);
    MockHttpServletRequest request = from("198.51.100.1");

    for (int i = 0; i < 3; i++) {
      assertThatNoException().isThrownBy(() -> throttle.check(request));
      throttle.recordAttempt(request);
    }
    clock.advance(Duration.ofMinutes(5).plusMillis(1));

    assertThat(retryAfter(throttle, request)).isEqualTo(Duration.ofSeconds(600));
  }

  @Test
  void retryAfterRoundsUpToAWholeSecondAndIsNeverZero() {
    IpThrottle throttle = throttle(1, 10);
    MockHttpServletRequest request = from("198.51.100.2");
    throttle.recordAttempt(request);

    clock.advance(WINDOW.minusMillis(1));

    assertThat(retryAfter(throttle, request)).isEqualTo(Duration.ofSeconds(1));
  }

  @Test
  void theWindowResetsOnceTheClockReachesItsEnd() {
    IpThrottle throttle = throttle(1, 10);
    MockHttpServletRequest request = from("198.51.100.3");
    throttle.recordAttempt(request);

    clock.advance(WINDOW);

    assertThatNoException().isThrownBy(() -> throttle.check(request));
    throttle.recordAttempt(request);
    assertThat(retryAfter(throttle, request)).isEqualTo(WINDOW);
  }

  @Test
  void eachAddressHasItsOwnCount() {
    IpThrottle throttle = throttle(1, 10);
    throttle.recordAttempt(from("198.51.100.4"));

    assertThatThrownBy(() -> throttle.check(from("198.51.100.4")))
        .isInstanceOf(TooManyRequestsException.class);
    assertThatNoException().isThrownBy(() -> throttle.check(from("198.51.100.5")));
  }

  @Test
  void theOldestAddressIsForgottenWhenTheMapIsFull() {
    IpThrottle throttle = throttle(1, 2);
    throttle.recordAttempt(from("198.51.100.6"));
    throttle.recordAttempt(from("198.51.100.7"));
    // Counting against a tracked address again does not make it "newer".
    throttle.recordAttempt(from("198.51.100.6"));

    throttle.recordAttempt(from("198.51.100.8"));

    assertThat(throttle.trackedAddresses()).isEqualTo(2);
    assertThatNoException().isThrownBy(() -> throttle.check(from("198.51.100.6")));
    assertThatThrownBy(() -> throttle.check(from("198.51.100.7")))
        .isInstanceOf(TooManyRequestsException.class);
    assertThatThrownBy(() -> throttle.check(from("198.51.100.8")))
        .isInstanceOf(TooManyRequestsException.class);
  }

  @Test
  void acquireCountsEveryRequest() {
    IpThrottle throttle = throttle(2, 10);
    MockHttpServletRequest request = from("198.51.100.9");

    throttle.acquire(request);
    throttle.acquire(request);

    assertThatThrownBy(() -> throttle.acquire(request))
        .isInstanceOf(TooManyRequestsException.class);
    // A refused request is not counted, so the window is not extended by hammering it.
    clock.advance(WINDOW);
    assertThatNoException().isThrownBy(() -> throttle.acquire(request));
  }

  @Test
  void theClientAddressIsTheRemoteAddressNotAForwardedHeader() {
    IpThrottle throttle = throttle(1, 10);
    MockHttpServletRequest spoofed = from("198.51.100.10");
    spoofed.addHeader("X-Forwarded-For", "203.0.113.99");
    throttle.recordAttempt(spoofed);

    MockHttpServletRequest sameSocket = from("198.51.100.10");
    sameSocket.addHeader("X-Forwarded-For", "203.0.113.100");
    assertThatThrownBy(() -> throttle.check(sameSocket))
        .isInstanceOf(TooManyRequestsException.class);
  }

  @Test
  void limitsMustBePositive() {
    assertThatThrownBy(() -> new ThrottleProperties.Limit(0, WINDOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ThrottleProperties.Limit(1, Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ThrottleProperties.Limit(1, null))
        .isInstanceOf(IllegalArgumentException.class);
    ThrottleProperties.Limit limit = new ThrottleProperties.Limit(1, WINDOW);
    assertThatThrownBy(() -> new ThrottleProperties(0, limit, limit))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ThrottleProperties(1, null, limit))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ThrottleProperties(1, limit, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("app.security.throttle.registration");
  }
}
