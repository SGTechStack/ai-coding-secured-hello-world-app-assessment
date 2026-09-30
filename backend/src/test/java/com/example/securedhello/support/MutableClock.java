package com.example.securedhello.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test seam for time (spec Seam 2): a clock that tests move forward. Import {@link Config} to make
 * it the application's {@code Clock}.
 */
public final class MutableClock extends Clock {

	private static final ZoneId ZONE = ZoneId.of("Asia/Singapore");

	private volatile Instant now = Instant.parse("2026-01-05T01:00:00Z");

	public void advance(Duration duration) {
		now = now.plus(duration);
	}

	@Override
	public Instant instant() {
		return now;
	}

	@Override
	public ZoneId getZone() {
		return ZONE;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return Clock.fixed(now, zone);
	}

	/** Replaces the application's {@code Clock} with one shared {@link MutableClock}. */
	@TestConfiguration(proxyBeanMethods = false)
	public static class Config {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock();
		}

	}

}
