package sg.securedhello.time;

import java.time.Clock;
import java.time.Instant;

import com.github.benmanes.caffeine.cache.Ticker;

import io.github.bucket4j.TimeMeter;

/**
 * The library clocks, built from a {@link Clock} (ADR-066): a Caffeine {@link Ticker} for cache expiry and a Bucket4j
 * {@link TimeMeter} for bucket refill. Both read the clock's instant in nanoseconds since the epoch, so neither is
 * monotonic: a wall-clock step moves refill and expiry, which is accepted (R-RL-004).
 */
public final class ClockTimes {

    private ClockTimes() {
    }

    /** A Caffeine ticker that reads {@code clock}. */
    public static Ticker ticker(Clock clock) {
        return () -> nanos(clock);
    }

    /** A Bucket4j time meter that reads {@code clock}. */
    public static TimeMeter timeMeter(Clock clock) {
        return new TimeMeter() {
            @Override
            public long currentTimeNanos() {
                return nanos(clock);
            }

            @Override
            public boolean isWallClockBased() {
                return true;
            }
        };
    }

    /** {@code clock}'s instant in nanoseconds since the epoch; fits a {@code long} until the year 2262. */
    static long nanos(Clock clock) {
        Instant now = clock.instant();
        return Math.addExact(Math.multiplyExact(now.getEpochSecond(), 1_000_000_000L), now.getNano());
    }
}
