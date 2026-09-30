package org.eds.demo.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A {@link Clock} tests can set and advance, for time-dependent behavior such as backoff. */
public class MutableClock extends Clock {

  private volatile Instant now;

  public MutableClock(Instant start) {
    this.now = start;
  }

  public void set(Instant instant) {
    this.now = instant;
  }

  public void advance(Duration duration) {
    this.now = now.plus(duration);
  }

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return this;
  }

  @Override
  public Instant instant() {
    return now;
  }
}
