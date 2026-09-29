package com.example.securedhello.ratelimit;

import java.time.Duration;

/**
 * An attempt was refused by a {@link RateLimiter} or the {@link IpThrottle}. Answered as 429
 * {@code too_many_requests} with a {@code Retry-After} header; the body never says which limit was
 * hit.
 */
public class RateLimitExceededException extends RuntimeException {

	private final Duration retryAfter;

	private final String reason;

	private final boolean keyedByClientAddress;

	RateLimitExceededException(Duration retryAfter, String reason, boolean keyedByClientAddress) {
		super("Rate limit exceeded");
		this.retryAfter = retryAfter;
		this.reason = reason;
		this.keyedByClientAddress = keyedByClientAddress;
	}

	/** Whole seconds until the next attempt is allowed, rounded up and at least 1. */
	public long retryAfterSeconds() {
		long seconds = retryAfter.toSeconds() + ((retryAfter.toNanosPart() > 0) ? 1 : 0);
		return Math.max(1, seconds);
	}

	/** The audit {@code event.reason}: {@code rate_limited} or {@code ip_throttled}. */
	public String reason() {
		return reason;
	}

	/** Whether the refused key is the client address, so the audit event carries its keyed hash. */
	public boolean keyedByClientAddress() {
		return keyedByClientAddress;
	}

}
