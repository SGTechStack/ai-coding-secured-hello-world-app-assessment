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

	private final RateLimiter registration;

	private final RateLimiter resetRequestByEmail;

	private final RateLimiter resetRequestByIp;

	private final RateLimiter resetConfirm;

	private final RateLimiter clientEvents;

	RateLimiters(RateLimitProperties properties, Clock clock) {
		this.login = new RateLimiter(properties.login().capacity(), properties.login().period(), clock, false);
		this.registration = new RateLimiter(properties.registration().capacity(), properties.registration().period(),
				clock, true);
		this.resetRequestByEmail = new RateLimiter(properties.resetRequestEmail().capacity(),
				properties.resetRequestEmail().period(), clock, false);
		this.resetRequestByIp = new RateLimiter(properties.resetRequestIp().capacity(),
				properties.resetRequestIp().period(), clock, true);
		this.resetConfirm = new RateLimiter(properties.resetConfirm().capacity(), properties.resetConfirm().period(),
				clock, true);
		this.clientEvents = new RateLimiter(properties.clientEvents().capacity(), properties.clientEvents().period(),
				clock, true);
	}

	/** Login attempts, keyed by lowercase username. */
	public RateLimiter login() {
		return login;
	}

	/** Registrations, keyed by direct client address. */
	public RateLimiter registration() {
		return registration;
	}

	/** Reset requests, keyed by lowercase email address. */
	public RateLimiter resetRequestByEmail() {
		return resetRequestByEmail;
	}

	/** Reset requests, keyed by direct client address. */
	public RateLimiter resetRequestByIp() {
		return resetRequestByIp;
	}

	/** Reset confirmations, keyed by direct client address (never per Account, ADR 0001). */
	public RateLimiter resetConfirm() {
		return resetConfirm;
	}

	/** Reports the SPA sends about itself, keyed by direct client address. */
	public RateLimiter clientEvents() {
		return clientEvents;
	}

	/** Empties every limiter. For tests only: lets each test start with full buckets. */
	public void resetAll() {
		login.reset();
		registration.reset();
		resetRequestByEmail.reset();
		resetRequestByIp.reset();
		resetConfirm.reset();
		clientEvents.reset();
	}

}
