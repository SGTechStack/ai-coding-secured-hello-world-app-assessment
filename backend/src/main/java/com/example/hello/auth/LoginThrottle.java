package com.example.hello.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class LoginThrottle {
  private static final int MAX_ADDRESSES = 10_000;
  private final Map<String, Bucket> buckets = new HashMap<>();
  private final Clock clock;

  public LoginThrottle(Clock clock) {
    this.clock = clock;
  }

  public <T> Optional<T> attempt(String address, Supplier<Optional<T>> authenticate) {
    Bucket bucket = acquireBucket(address);
    try {
      synchronized (bucket) {
        if (bucket.failures >= 3) throw new ThrottledException();
        Optional<T> result = authenticate.get();
        if (result.isEmpty()) {
          bucket.failures++;
          // Keep protection for fifteen minutes after the last failure, not bucket creation.
          bucket.expiresAt = clock.instant().plusSeconds(900);
        }
        return result;
      }
    } finally {
      releaseBucket(bucket);
    }
  }

  private synchronized Bucket acquireBucket(String address) {
    Instant now = clock.instant();
    // Never replace a bucket while a request is checking credentials or waiting on it.
    buckets
        .entrySet()
        .removeIf(
            entry ->
                entry.getValue().activeRequests == 0 && !entry.getValue().expiresAt.isAfter(now));
    if (!buckets.containsKey(address) && buckets.size() >= MAX_ADDRESSES)
      throw new ThrottledException();
    Bucket bucket = buckets.computeIfAbsent(address, ignored -> new Bucket(now.plusSeconds(900)));
    bucket.activeRequests++;
    return bucket;
  }

  private synchronized void releaseBucket(Bucket bucket) {
    bucket.activeRequests--;
  }

  private static class Bucket {
    private volatile Instant expiresAt;
    private int failures;
    private int activeRequests;

    Bucket(Instant expiresAt) {
      this.expiresAt = expiresAt;
    }
  }
}
