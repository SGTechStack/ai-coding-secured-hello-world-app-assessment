package com.example.securedhello.ratelimit;

import java.util.function.Function;

/** The rate limiters keyed by direct client address, named for {@link RateLimitedByClientAddress}. */
public enum ClientAddressLimit {

	REGISTRATION(RateLimiters::registration),

	RESET_REQUEST(RateLimiters::resetRequestByIp),

	RESET_CONFIRM(RateLimiters::resetConfirm),

	CLIENT_EVENTS(RateLimiters::clientEvents);

	private final Function<RateLimiters, RateLimiter> limiter;

	ClientAddressLimit(Function<RateLimiters, RateLimiter> limiter) {
		this.limiter = limiter;
	}

	RateLimiter in(RateLimiters rateLimiters) {
		return limiter.apply(rateLimiters);
	}

}
