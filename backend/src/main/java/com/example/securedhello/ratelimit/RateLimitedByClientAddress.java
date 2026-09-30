package com.example.securedhello.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a public handler method whose per-client-address limiter must be acquired before its request
 * body is bound and validated. {@link RateLimitBeforeBindingInterceptor} does the acquiring, so a
 * malformed body spends the caller's quota instead of writing an unrated {@code validation} audit
 * event (issue 18). A limiter keyed by something in the body (the reset request's per-email limit)
 * cannot run this early and stays in the handler.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimitedByClientAddress {

	/** The limiter to acquire, keyed by the direct client address. */
	ClientAddressLimit value();

}
