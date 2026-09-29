package com.sgtechstack.helloauth.throttle;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Fixed-window attempt limiter keyed by an arbitrary string (here: client IP).
 * <p>
 * Attempts are reserved atomically <em>before</em> the guarded work runs, so a burst of parallel
 * requests cannot overshoot the limit. State is in memory and per instance, bounded in size so a
 * flood of distinct keys cannot exhaust the heap; a multi-instance deployment needs a shared store.
 */
public final class AttemptThrottle {

	private static final long MAX_TRACKED_KEYS = 100_000;

	private final int maxAttempts;

	private final Duration window;

	private final Clock clock;

	private final Cache<String, Window> windows;

	public AttemptThrottle(int maxAttempts, Duration window, Clock clock) {
		this.maxAttempts = maxAttempts;
		this.window = window;
		this.clock = clock;
		this.windows = Caffeine.newBuilder().expireAfterWrite(window).maximumSize(MAX_TRACKED_KEYS).build();
	}

	/**
	 * Reserves one attempt for {@code key}.
	 * @return empty if the attempt may proceed, otherwise how long until the window reopens
	 */
	public Optional<Duration> tryAcquire(String key) {
		Instant now = this.clock.instant();
		AtomicReference<Duration> retryAfter = new AtomicReference<>();
		this.windows.asMap().compute(key, (k, current) -> {
			if (current == null || current.hasEndedAt(now, this.window)) {
				return new Window(now, 1);
			}
			if (current.attempts() >= this.maxAttempts) {
				retryAfter.set(Duration.between(now, current.start().plus(this.window)));
				return current;
			}
			return new Window(current.start(), current.attempts() + 1);
		});
		return Optional.ofNullable(retryAfter.get());
	}

	/**
	 * Returns a reserved attempt that turned out not to count, such as a successful login.
	 */
	public void release(String key) {
		this.windows.asMap()
			.computeIfPresent(key,
					(k, current) -> (current.attempts() <= 1) ? null
							: new Window(current.start(), current.attempts() - 1));
	}

	private record Window(Instant start, int attempts) {

		boolean hasEndedAt(Instant now, Duration length) {
			return !now.isBefore(this.start.plus(length));
		}

	}

}
