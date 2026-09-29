package sg.securedhello.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.github.bucket4j.Bucket;

import org.junit.jupiter.api.Test;

import sg.securedhello.testsupport.MutableClock;
import sg.securedhello.testsupport.Proves;

/** The library clocks read the injected {@code Clock}, so tests move buckets and caches by moving it (ADR-066). */
class ClockTimesTest {

    private final MutableClock clock = MutableClock.startingAt(Instant.parse("2026-01-01T00:00:00.123456789Z"));

    @Test
    @Proves("T-RL-018")
    void advancingTheClockRefillsABucket() {
        Bucket bucket = Bucket.builder().withCustomTimePrecision(ClockTimes.timeMeter(clock))
                .addLimit(limit -> limit.capacity(1).refillGreedy(1, Duration.ofMinutes(1)))
                .build();

        assertThat(bucket.tryConsume(1)).isTrue();
        assertThat(bucket.tryConsume(1)).isFalse();
        clock.advance(Duration.ofSeconds(59));
        assertThat(bucket.tryConsume(1)).isFalse();
        clock.advance(Duration.ofSeconds(1));
        assertThat(bucket.tryConsume(1)).isTrue();
    }

    @Test
    @Proves("T-RL-018")
    void advancingTheClockExpiresACacheEntry() {
        Cache<String, String> cache = Caffeine.newBuilder().ticker(ClockTimes.ticker(clock))
                .expireAfterWrite(Duration.ofMinutes(1)).build();
        cache.put("key", "value");

        clock.advance(Duration.ofSeconds(59));
        assertThat(cache.getIfPresent("key")).isEqualTo("value");
        clock.advance(Duration.ofSeconds(1));
        assertThat(cache.getIfPresent("key")).isNull();
    }

    @Test
    void bothReadTheClocksInstantInNanoseconds() {
        long expected = Instant.parse("2026-01-01T00:00:00Z").getEpochSecond() * 1_000_000_000L + 123_456_789L;

        assertThat(ClockTimes.ticker(clock).read()).isEqualTo(expected);
        assertThat(ClockTimes.timeMeter(clock).currentTimeNanos()).isEqualTo(expected);
        assertThat(ClockTimes.timeMeter(clock).isWallClockBased()).isTrue();
    }
}
