package org.eds.demo.auth.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Remembers recent failed sign-ins per address, in memory and independent of any Account, so one
 * address cannot spray guesses across many usernames. State is per instance and lost on restart.
 */
@Component
@RequiredArgsConstructor
class SignInIpThrottle {

  /** Address count above which expired entries are swept, bounding memory under a spray attack. */
  private static final int SWEEP_ABOVE_ADDRESSES = 10_000;

  private final SignInThrottleProperties properties;
  private final Clock clock;
  private final ConcurrentHashMap<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

  /** How long the address must wait, or empty when it may try. */
  Optional<Duration> retryAfter(String address) {
    var now = clock.instant();
    var recent = failures.compute(address, (key, existing) -> pruned(existing, now));
    if (recent == null || recent.size() < properties.ipMaxFailures()) {
      return Optional.empty();
    }
    return Optional.of(Duration.between(now, recent.peekFirst().plus(properties.ipWindow())));
  }

  void recordFailure(String address) {
    var now = clock.instant();
    if (failures.size() > SWEEP_ABOVE_ADDRESSES) {
      failures.replaceAll((key, existing) -> pruned(existing, now));
    }
    failures.compute(
        address,
        (key, existing) -> {
          var recent = pruned(existing, now);
          var updated = recent == null ? new ArrayDeque<Instant>() : recent;
          updated.addLast(now);
          return updated;
        });
  }

  /** Drops failures that left the window; null when none remain so the entry is removed. */
  private Deque<Instant> pruned(Deque<Instant> recent, Instant now) {
    if (recent == null) {
      return null;
    }
    var cutoff = now.minus(properties.ipWindow());
    while (!recent.isEmpty() && !recent.peekFirst().isAfter(cutoff)) {
      recent.removeFirst();
    }
    return recent.isEmpty() ? null : recent;
  }
}
