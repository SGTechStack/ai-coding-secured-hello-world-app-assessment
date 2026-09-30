package com.assessment.auth.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * The test {@link Clock} (spec.md S12).
 *
 * <p>Every deadline in this application is read through the injectable {@code Clock}: the 20-minute
 * lockout cooldown, the 8-hour absolute session timeout and the 30-minute reset-token TTL. Advancing
 * this clock is how all three are tested, and <strong>no test in this suite sleeps to pass time</strong>.
 *
 * <p>One thing it deliberately does not control: Spring Session's own {@code lastAccessedTime}, which
 * reads {@code System.currentTimeMillis()} inside the framework. The 15-minute <em>idle</em> timeout
 * is therefore not clock-testable and is asserted as configuration rather than behaviour.
 */
public class MutableClock extends Clock {

  private volatile Instant instant = origin();

  /**
   * Real wall-clock time, sampled per reset, <strong>not</strong> a hard-coded literal.
   *
   * <p>A fixed literal origin looks more deterministic and is actively wrong here. Two clocks are in
   * play and only one of them is injectable: {@code AbsoluteSessionTimeoutFilter} compares
   * {@code clock.instant()} against {@code HttpSession.getCreationTime()}, which the servlet
   * container stamps from {@code System.currentTimeMillis()} and no test can influence. Pin this
   * clock to a date in the past and every session looks as though it were created in the future, so
   * the 8-hour timeout can never elapse — the filter is unobservable and its test passes or fails for
   * reasons unrelated to the filter.
   *
   * <p>What matters for the tests that <em>do</em> depend on determinism is that the clock does not
   * tick on its own between an arrange and an assert, and that holds: it moves only when
   * {@link #advance} is called.
   *
   * <p>Truncated to milliseconds because a stored timestamp is compared against this value directly.
   * {@code Instant.now()} carries sub-microsecond digits on this platform; PostgreSQL's
   * {@code timestamptz} keeps microseconds and H2 rounds differently again, so an untruncated origin
   * fails an exact-equality assertion on a round-trip — as a precision artefact that reads like a
   * clock defect.
   */
  private static Instant origin() {
    return Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
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
    return instant;
  }

  public void advance(Duration amount) {
    instant = instant.plus(amount);
  }

  public void set(Instant value) {
    instant = value;
  }

  /** Called from the base class's {@code @BeforeEach} so tests never inherit each other's time. */
  public void reset() {
    instant = origin();
  }
}
