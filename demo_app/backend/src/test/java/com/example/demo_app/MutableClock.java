package com.example.demo_app;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * A UTC {@link Clock} that stands still until a test moves it, so tests can step past lockout,
 * throttle windows and token expiry without sleeping. Supplied by {@link TestClockConfig}.
 */
public final class MutableClock extends Clock {

  private volatile Instant now;

  public MutableClock(Instant start) {
    this.now = start;
  }

  /** Moves the clock forward by {@code duration}. */
  public void advance(Duration duration) {
    now = now.plus(duration);
  }

  /** Sets the clock to {@code instant}. */
  public void set(Instant instant) {
    now = instant;
  }

  @Override
  public Instant instant() {
    return now;
  }

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return Clock.fixed(now, zone);
  }
}
