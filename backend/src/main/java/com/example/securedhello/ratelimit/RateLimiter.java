package com.example.securedhello.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An in-memory rate limiter with one bucket per key: at most {@code capacity} attempts per
 * {@code period}, refilled evenly over the period (so after a full burst one more attempt is allowed
 * every {@code period / capacity}). Time comes from the injected {@link Clock}.
 * <p>
 * Each bucket is stored as the instant it will be full again (the generic cell rate algorithm), so
 * the arithmetic is exact. A refused attempt does not use up capacity. Buckets that have refilled
 * completely are forgotten once many keys are tracked, which keeps memory bounded by the keys active
 * within one period.
 */
public final class RateLimiter {

	private static final int PURGE_THRESHOLD = 10_000;

	private final Duration interval;

	private final Duration burstTolerance;

	private final Clock clock;

	private final Map<String, Instant> fullAgainAt = new ConcurrentHashMap<>();

	RateLimiter(int capacity, Duration period, Clock clock) {
		this.interval = period.dividedBy(capacity);
		this.burstTolerance = period.minus(this.interval);
		this.clock = clock;
	}

	/**
	 * Takes one attempt from the key's bucket.
	 * @throws RateLimitExceededException if the bucket is empty, saying how long until the next
	 *         attempt is allowed
	 */
	public void acquire(String key) {
		Instant now = clock.instant();
		if (fullAgainAt.size() >= PURGE_THRESHOLD) {
			fullAgainAt.values().removeIf((instant) -> !instant.isAfter(now));
		}
		Duration[] wait = new Duration[1];
		fullAgainAt.compute(key, (k, current) -> {
			Instant start = (current == null || current.isBefore(now)) ? now : current;
			Duration ahead = Duration.between(now, start);
			if (ahead.compareTo(burstTolerance) > 0) {
				wait[0] = ahead.minus(burstTolerance);
				return current;
			}
			return start.plus(interval);
		});
		if (wait[0] != null) {
			throw new RateLimitExceededException(wait[0]);
		}
	}

	/** Empties every bucket's history. For tests only. */
	void reset() {
		fullAgainAt.clear();
	}

}
