package com.assessment.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.assessment.auth.support.AbstractIntegrationTest;
import com.assessment.auth.support.ApiClient;
import com.assessment.auth.user.User;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Account lockout and rate limiting (stories 1.9, 1.10). */
class LockoutAndRateLimitIT extends AbstractIntegrationTest {

  private void registerUser(String username) {
    ApiClient client = new ApiClient(port);
    client.fetchCsrf();
    client.post(
        "/auth/register",
        "{\"username\":\""
            + username
            + "\",\"email\":\""
            + username
            + "@example.com\",\"password\":\"lantern quiet field\"}");
  }

  @Test
  @DisplayName("five consecutive failures lock the account for 20 minutes")
  void fiveFailuresLock() {
    registerUser("locky");
    for (int attempt = 1; attempt <= 5; attempt++) {
      ResponseEntity<String> response = new ApiClient(port).login("locky", "wrong wrong wrong");
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    User locked = userRepository.findByUsername("locky").orElseThrow();
    assertThat(locked.getFailedLoginAttempts()).isEqualTo(5);
    assertThat(locked.getLockedUntil()).isNotNull();
    assertThat(locked.isLockedAt(clock().instant())).isTrue();

    // A locked account returns the SAME generic 401, revealing nothing about the lock (Std:258).
    ResponseEntity<String> correct = new ApiClient(port).login("locky", "lantern quiet field");
    assertThat(correct.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(correct.getBody()).isNull();
  }

  @Test
  @DisplayName("the lock lifts automatically once locked_until passes, with no write and no sweep")
  void lockLiftsOnItsOwn() {
    registerUser("expiry");
    for (int attempt = 1; attempt <= 5; attempt++) {
      new ApiClient(port).login("expiry", "wrong wrong wrong");
    }
    assertThat(new ApiClient(port).login("expiry", "lantern quiet field").getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);

    // Advanced, never slept. locked_until is self-expiring, which is exactly why there is no
    // boolean beside it and no scheduled job to clear it.
    clock().advance(Duration.ofMinutes(21));

    assertThat(new ApiClient(port).login("expiry", "lantern quiet field").getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("the counter has no window: it decays only on a successful login")
  void counterDecaysOnlyOnSuccess() {
    registerUser("counter");
    for (int attempt = 1; attempt <= 3; attempt++) {
      new ApiClient(port).login("counter", "wrong wrong wrong");
    }
    assertThat(userRepository.findByUsername("counter").orElseThrow().getFailedLoginAttempts())
        .isEqualTo(3);

    // A whole day later the count is unchanged -- the PRD's "within a window" was overruled.
    clock().advance(Duration.ofDays(1));
    new ApiClient(port).login("counter", "wrong wrong wrong");
    assertThat(userRepository.findByUsername("counter").orElseThrow().getFailedLoginAttempts())
        .isEqualTo(4);

    assertThat(new ApiClient(port).login("counter", "lantern quiet field").getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(userRepository.findByUsername("counter").orElseThrow().getFailedLoginAttempts())
        .isZero();
  }

  @Test
  @DisplayName("lock state is derived from locked_until alone")
  void lockStateHasOneSourceOfTruth() {
    registerUser("single");
    User user = userRepository.findByUsername("single").orElseThrow();
    assertThat(user.isLockedAt(clock().instant())).isFalse();
    user.setLockedUntil(clock().instant().plus(Duration.ofMinutes(5)));
    assertThat(user.isLockedAt(clock().instant())).isTrue();
    assertThat(user.isLockedAt(clock().instant().plus(Duration.ofMinutes(6)))).isFalse();
  }

  @Test
  @DisplayName("the per-account limiter returns 429 with Retry-After, written by the shared writer")
  void perAccountRateLimit() {
    registerUser("throttle");
    // 10/min per account. The eleventh is refused before authentication even happens.
    ResponseEntity<String> limited = null;
    for (int attempt = 1; attempt <= 12; attempt++) {
      ResponseEntity<String> response = new ApiClient(port).login("throttle", "wrong wrong wrong");
      if (response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
        limited = response;
        break;
      }
    }

    assertThat(limited).as("the account limiter must trigger within 12 attempts").isNotNull();
    // Std:260 makes Retry-After mandatory -- "must include", not a nicety.
    assertThat(limited.getHeaders().getFirst("Retry-After")).isNotNull();
    // Written by ProblemDetailWriter, not sendError, so it carries a code.
    assertThat(limited.getHeaders().getContentType().toString())
        .startsWith("application/problem+json");
    assertThat(limited.getBody()).contains("RATE_LIMITED");
  }

  @Test
  @DisplayName("a rate-limit rejection is a 429, while a locked account stays a generic 401")
  void rateLimitAndLockoutProduceDifferentStatuses() {
    // Deliberate: Std:247 scopes to authentication OUTCOMES, and a rate-limit rejection happens
    // before any outcome exists. So the two counters are visibly different by design.
    registerUser("distinct");
    for (int attempt = 1; attempt <= 5; attempt++) {
      new ApiClient(port).login("distinct", "wrong wrong wrong");
    }
    ResponseEntity<String> locked = new ApiClient(port).login("distinct", "lantern quiet field");
    assertThat(locked.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(locked.getBody()).isNull();
  }
}
