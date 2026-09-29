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

    public LoginThrottle(Clock clock) { this.clock = clock; }

    public <T> Optional<T> attempt(String address, Supplier<Optional<T>> authenticate) {
        Bucket bucket = bucketFor(address);
        synchronized (bucket) {
            if (bucket.failures >= 3) throw new ThrottledException();
            Optional<T> result = authenticate.get();
            if (result.isEmpty()) bucket.failures++;
            return result;
        }
    }

    private synchronized Bucket bucketFor(String address) {
        Instant now = clock.instant();
        buckets.entrySet().removeIf(entry -> !entry.getValue().expiresAt.isAfter(now));
        if (!buckets.containsKey(address) && buckets.size() >= MAX_ADDRESSES) throw new ThrottledException();
        return buckets.computeIfAbsent(address, ignored -> new Bucket(now.plusSeconds(900)));
    }

    private static class Bucket {
        private final Instant expiresAt;
        private int failures;
        Bucket(Instant expiresAt) { this.expiresAt = expiresAt; }
    }
}
