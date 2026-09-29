package com.sgtechstack.helloauth.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A clock tests can move forward, to cross lockout and token-expiry boundaries without waiting.
 */
public final class MutableClock extends Clock {

	// Millisecond precision survives a round trip through the database unchanged.
	private final AtomicReference<Instant> now = new AtomicReference<>(Instant.now().truncatedTo(ChronoUnit.MILLIS));

	public void advance(Duration duration) {
		this.now.updateAndGet(instant -> instant.plus(duration));
	}

	@Override
	public Instant instant() {
		return this.now.get();
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		throw new UnsupportedOperationException("MutableClock is always UTC");
	}

}
