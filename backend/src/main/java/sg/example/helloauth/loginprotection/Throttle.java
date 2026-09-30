package sg.example.helloauth.loginprotection;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;

import sg.example.helloauth.loginprotection.LoginProtectionProperties.Limit;

/**
 * A limit on attempts per key, such as per client IP: a token bucket for each key, refilled
 * steadily on the application {@link Clock}. Buckets live in memory, so the limit holds for a
 * single instance only (ADR-0003). An idle bucket is dropped once it would be full again, and the
 * number of keys is capped, so a flood of new keys can't exhaust memory; at the cap, the least
 * recently used buckets go first.
 */
final class Throttle {

    private static final long MAX_KEYS = 100_000;

    private final Bandwidth limit;
    private final TimeMeter time;
    private final Cache<String, Bucket> buckets;

    Throttle(Limit limit, Clock clock) {
        this.limit = Bandwidth.builder().capacity(limit.attempts()).refillGreedy(limit.attempts(), limit.period())
                .build();
        this.time = new ClockTimeMeter(clock);
        this.buckets = Caffeine.newBuilder().expireAfterAccess(limit.period()).maximumSize(MAX_KEYS).build();
    }

    /** Takes one attempt for the key: empty if it may go ahead, otherwise how long until one may. */
    Optional<Duration> tryAcquire(String key) {
        ConsumptionProbe probe = buckets.get(key, this::newBucket).tryConsumeAndReturnRemaining(1);
        return probe.isConsumed() ? Optional.empty() : Optional.of(Duration.ofNanos(probe.getNanosToWaitForRefill()));
    }

    /** Gives back an attempt that turned out not to count against the key. */
    void release(String key) {
        Bucket bucket = buckets.getIfPresent(key);
        if (bucket != null) {
            bucket.addTokens(1);
        }
    }

    private Bucket newBucket(String key) {
        return Bucket.builder().addLimit(limit).withCustomTimePrecision(time).build();
    }

    private record ClockTimeMeter(Clock clock) implements TimeMeter {

        @Override
        public long currentTimeNanos() {
            Instant now = clock.instant();
            return now.getEpochSecond() * 1_000_000_000L + now.getNano();
        }

        @Override
        public boolean isWallClockBased() {
            return true;
        }
    }
}
