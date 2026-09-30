package com.example.securedhello.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.example.securedhello.config.IpThrottleProperties;

/**
 * The IP Throttle: an in-memory sliding count of failed logins per direct client address (forwarded
 * headers are never consulted). When {@code threshold} failures fall within {@code window}, the
 * address is blocked for {@code blockDuration}, whichever usernames were tried. It is independent of
 * Account lockout: it never touches an Account, and a Locked Account never blocks an address. In
 * memory, so not shared between instances (ADR 0001). Time comes from the injected {@link Clock}.
 * <p>
 * Addresses with no failure inside the window and no active block are forgotten once many are
 * tracked, which keeps memory bounded by the addresses active within one window.
 */
@Component
public class IpThrottle {

	private static final int PURGE_THRESHOLD = 10_000;

	private static final String REASON = "ip_throttled";

	private final int threshold;

	private final Duration window;

	private final Duration blockDuration;

	private final Clock clock;

	private final Map<String, Entry> entries = new ConcurrentHashMap<>();

	IpThrottle(IpThrottleProperties properties, Clock clock) {
		this.threshold = properties.threshold();
		this.window = properties.window();
		this.blockDuration = properties.blockDuration();
		this.clock = clock;
	}

	/**
	 * Refuses the attempt if the address is blocked.
	 * @throws RateLimitExceededException while the address is blocked, saying how long remains
	 */
	public void check(String address) {
		Entry entry = entries.get(address);
		if (entry == null) {
			return;
		}
		Duration remaining = entry.blockRemaining(clock.instant());
		if (remaining != null) {
			throw new RateLimitExceededException(remaining, REASON, true);
		}
	}

	/** Counts one failed login from the address, blocking it when the threshold is reached. */
	public void recordFailure(String address) {
		Instant now = clock.instant();
		if (entries.size() >= PURGE_THRESHOLD) {
			entries.values().removeIf((entry) -> entry.isIdle(now, window));
		}
		entries.compute(address, (key, current) -> {
			Entry entry = (current != null) ? current : new Entry();
			entry.recordFailure(now, window, threshold, blockDuration);
			return entry;
		});
	}

	/** Forgets every address. For tests only. */
	public void reset() {
		entries.clear();
	}

	/** Failures and block for one address; synchronized because the purge reads it outside {@code compute}. */
	private static final class Entry {

		private final Deque<Instant> failures = new ArrayDeque<>();

		private Instant blockedUntil;

		synchronized void recordFailure(Instant now, Duration window, int threshold, Duration blockDuration) {
			Instant cutoff = now.minus(window);
			while (!failures.isEmpty() && !failures.peekFirst().isAfter(cutoff)) {
				failures.pollFirst();
			}
			failures.addLast(now);
			if (failures.size() >= threshold) {
				blockedUntil = now.plus(blockDuration);
				failures.clear();
			}
		}

		/** Time left on the block, or null when the address is not blocked. */
		synchronized Duration blockRemaining(Instant now) {
			return (blockedUntil != null && blockedUntil.isAfter(now)) ? Duration.between(now, blockedUntil) : null;
		}

		synchronized boolean isIdle(Instant now, Duration window) {
			Instant last = failures.peekLast();
			return blockRemaining(now) == null && (last == null || !last.isAfter(now.minus(window)));
		}

	}

}
