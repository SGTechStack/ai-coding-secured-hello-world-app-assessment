package com.example.securedhello.ratelimit;

import java.time.Clock;

import org.springframework.stereotype.Component;

import com.example.securedhello.config.RateLimitProperties;

/**
 * Every rate limiter in the application. In memory, so not shared between instances (the API runs as
 * a single instance, ADR 0001).
 */
@Component
public class RateLimiters {

	private final RateLimiter login;

	RateLimiters(RateLimitProperties properties, Clock clock) {
		this.login = new RateLimiter(properties.login().capacity(), properties.login().period(), clock);
	}

	/** Login attempts, keyed by lowercase username. */
	public RateLimiter login() {
		return login;
	}

	/** Empties every limiter. For tests only: lets each test start with full buckets. */
	public void resetAll() {
		login.reset();
	}

}
