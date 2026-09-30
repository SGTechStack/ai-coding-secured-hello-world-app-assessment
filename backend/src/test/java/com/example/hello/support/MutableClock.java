package com.example.hello.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock tests can move forward to exercise lockout cooldowns and token expiry. */
public class MutableClock extends Clock {

  private Instant now;

  public MutableClock(Instant start) {
    this.now = start;
  }

  public static MutableClock at(String isoInstant) {
    return new MutableClock(Instant.parse(isoInstant));
  }

  public void advance(Duration amount) {
    now = now.plus(amount);
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
