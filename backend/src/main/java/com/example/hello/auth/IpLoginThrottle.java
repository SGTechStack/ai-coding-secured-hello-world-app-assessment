package com.example.hello.auth;

import com.example.hello.config.AppSecurityProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Story 3, network half: sliding-window count of failed logins per client IP, independent of
 * which usernames were tried. Keeps one attacker from locking out many accounts and blunts
 * credential stuffing. In-memory and per-instance; move to a shared store (Redis) when
 * running several backend nodes.
 */
@Component
public class IpLoginThrottle {

  private static final int SWEEP_THRESHOLD = 10_000;

  private final Map<String, Deque<Instant>> failuresByIp = new ConcurrentHashMap<>();
  private final int maxFailures;
  private final Duration window;
  private final Clock clock;

  @Autowired
  public IpLoginThrottle(AppSecurityProperties props, Clock clock) {
    this(props.login().ipMaxFailures(), props.login().ipWindow(), clock);
  }

  IpLoginThrottle(int maxFailures, Duration window, Clock clock) {
    this.maxFailures = maxFailures;
    this.window = window;
    this.clock = clock;
  }

  public boolean isThrottled(String ip) {
    Deque<Instant> failures = failuresByIp.get(ip);
    if (failures == null) {
      return false;
    }
    synchronized (failures) {
      prune(failures, clock.instant());
      return failures.size() >= maxFailures;
    }
  }

  public void recordFailure(String ip) {
    Instant now = clock.instant();
    Deque<Instant> failures = failuresByIp.computeIfAbsent(ip, key -> new ArrayDeque<>());
    synchronized (failures) {
      prune(failures, now);
      failures.addLast(now);
    }
    if (failuresByIp.size() > SWEEP_THRESHOLD) {
      sweep(now);
    }
  }

  /** Test hook: forget every recorded failure. */
  public void clear() {
    failuresByIp.clear();
  }

  private void prune(Deque<Instant> failures, Instant now) {
    Instant cutoff = now.minus(window);
    while (!failures.isEmpty() && failures.peekFirst().isBefore(cutoff)) {
      failures.pollFirst();
    }
  }

  private void sweep(Instant now) {
    failuresByIp
        .entrySet()
        .removeIf(
            entry -> {
              Deque<Instant> failures = entry.getValue();
              synchronized (failures) {
                prune(failures, now);
                return failures.isEmpty();
              }
            });
  }
}
