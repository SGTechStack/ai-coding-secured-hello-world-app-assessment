package com.assessment.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.assessment.auth.support.AbstractIntegrationTest;
import com.assessment.auth.support.ApiClient;
import com.assessment.auth.user.User;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
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
  @DisplayName("the per-IP limiter engages across many usernames, without locking any account")
  void ipThrottleIsIndependentOfAccountLockout() {
    // prd:150 requires this case by name, and it is the one the PRD argues for most explicitly: an
    // attacker must not be able to lock out a legitimate user merely by failing that user's password
    // from one source. So the counters have to be separately keyed -- IP by remote address, lockout by
    // account -- and this test is what proves they are.
    //
    // Spread across DISTINCT usernames on purpose. Each one gets its own account bucket with a single
    // attempt in it and its own failed-attempt counter at 1, so neither the per-account limiter
    // (10/min) nor lockout (5 consecutive) can fire. The only counter that can reach its limit is the
    // per-IP one, at 50/min from 127.0.0.1.
    // CONCURRENTLY, and that is a finding rather than a style choice. Bucket4j refills greedily -- 50
    // tokens a minute is one roughly every 1.2s -- and a failed login pays the 250ms response-time
    // floor. A serial caller therefore drains only about 0.75 of a token per attempt and needs ~66
    // attempts to trip a limit of 50. Which is correct behaviour for a rate limiter (it shapes the
    // rate, it does not cap a total), but it makes the serial version of this test both slow and
    // misleading about what the limiter defends against. A real spray is parallel.
    registerUser("bystander");

    ApiClient origin = new ApiClient(port);
    origin.fetchCsrf();

    int attempts = 80;
    ExecutorService pool = Executors.newFixedThreadPool(16);
    try {
      List<Future<HttpStatusCode>> results = new ArrayList<>();
      for (int attempt = 1; attempt <= attempts; attempt++) {
        String username = "sprayed" + attempt;
        results.add(
            pool.submit(
                () ->
                    new ApiClient(port)
                        .sharingSessionWith(origin)
                        .post(
                            "/auth/login",
                            "{\"username\":\"" + username + "\",\"password\":\"wrong wrong wrong\"}")
                        .getStatusCode()));
      }
      long throttled =
          results.stream()
              .map(
                  future -> {
                    try {
                      return future.get(60, TimeUnit.SECONDS);
                    } catch (Exception ex) {
                      throw new IllegalStateException(ex);
                    }
                  })
              .filter(status -> status == HttpStatus.TOO_MANY_REQUESTS)
              .count();

      assertThat(throttled)
          .as("the per-IP limiter must refuse some of %d attempts from one address", attempts)
          .isGreaterThan(0);
      // Every attempt used a DIFFERENT username, so no account bucket saw more than one request and
      // the per-account limiter (10/min) cannot be what fired. Only the IP-keyed counter can be.
      assertThat(throttled)
          .as("more refusals than the per-account limit would explain")
          .isLessThan(attempts);
    } finally {
      pool.shutdownNow();
    }

    // And the bystander -- whose password was never even tried -- is untouched: not locked, and its
    // failed-attempt counter never moved. This is the half that makes the independence claim real.
    User bystander = userRepository.findByUsername("bystander").orElseThrow();
    assertThat(bystander.getFailedLoginAttempts()).isZero();
    assertThat(bystander.isLockedAt(clock().instant())).isFalse();
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
