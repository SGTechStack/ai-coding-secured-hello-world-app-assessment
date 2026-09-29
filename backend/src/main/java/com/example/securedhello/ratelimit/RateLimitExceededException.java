package com.example.securedhello.ratelimit;

import java.time.Duration;

/**
 * An attempt was refused by a {@link RateLimiter}. Answered as 429 {@code too_many_requests} with a
 * {@code Retry-After} header.
 */
public class RateLimitExceededException extends RuntimeException {

	private final Duration retryAfter;

	RateLimitExceededException(Duration retryAfter) {
		super("Rate limit exceeded");
		this.retryAfter = retryAfter;
	}

	/** Whole seconds until the next attempt is allowed, rounded up and at least 1. */
	public long retryAfterSeconds() {
		long seconds = retryAfter.toSeconds() + ((retryAfter.toNanosPart() > 0) ? 1 : 0);
		return Math.max(1, seconds);
	}

}
