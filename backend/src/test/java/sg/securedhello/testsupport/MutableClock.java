package sg.securedhello.testsupport;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A forward-only clock that stands still until a test advances it (ADR-066).
 *
 * <p>It never moves backwards, so our time stays at or ahead of framework code that reads {@code Instant.now()}
 * directly (Spring Session, {@code FactorGrantedAuthority}). To test the framework's own clock, age the stored data
 * instead of moving this one.
 */
public final class MutableClock extends Clock {

    private final AtomicReference<Instant> now;
    private final ZoneId zone;

    private MutableClock(AtomicReference<Instant> now, ZoneId zone) {
        this.now = now;
        this.zone = zone;
    }

    /** A clock starting at the current real time, in UTC. */
    public static MutableClock startingNow() {
        return startingAt(Clock.systemUTC().instant());
    }

    /** A clock starting at {@code start}, in UTC. */
    public static MutableClock startingAt(Instant start) {
        return new MutableClock(new AtomicReference<>(Objects.requireNonNull(start)), ZoneOffset.UTC);
    }

    /**
     * Moves the clock forward by {@code amount} and returns the new instant.
     *
     * @throws IllegalArgumentException if {@code amount} is negative
     */
    public Instant advance(Duration amount) {
        if (amount.isNegative()) {
            throw new IllegalArgumentException("A forward-only clock cannot move back by " + amount);
        }
        return now.updateAndGet(current -> current.plus(amount));
    }

    @Override
    public Instant instant() {
        return now.get();
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    /** A view in another zone that shares this clock's timeline. */
    @Override
    public MutableClock withZone(ZoneId newZone) {
        return new MutableClock(now, Objects.requireNonNull(newZone));
    }
}
