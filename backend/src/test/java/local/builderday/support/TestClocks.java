package local.builderday.support;

import io.github.bucket4j.TimeMeter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** One controllable wall clock that drives both the security {@link Clock} and the rate-limit {@link TimeMeter}. */
@TestConfiguration
public class TestClocks {
  private final AtomicLong offsetMillis = new AtomicLong();
  private volatile Long frozenMillis;

  public void advance(Duration duration) { offsetMillis.addAndGet(duration.toMillis()); }

  /** Stops the clock at the current instant, so timestamps are deterministic; {@link #advance} still moves it. */
  public Instant freeze() {
    frozenMillis = nowMillis();
    offsetMillis.set(0);
    return now();
  }

  public void reset() {
    frozenMillis = null;
    offsetMillis.set(0);
  }

  long nowMillis() {
    Long frozen = frozenMillis;
    return (frozen == null ? System.currentTimeMillis() : frozen) + offsetMillis.get();
  }

  public Instant now() { return Instant.ofEpochMilli(nowMillis()); }

  @Bean @Primary
  Clock testClock() {
    return new Clock() {
      @Override public ZoneId getZone() { return ZoneOffset.UTC; }
      @Override public Clock withZone(ZoneId zone) { return this; }
      @Override public Instant instant() { return Instant.ofEpochMilli(nowMillis()); }
    };
  }

  @Bean @Primary
  TimeMeter testTimeMeter() {
    return new TimeMeter() {
      @Override public long currentTimeNanos() { return nowMillis() * 1_000_000L; }
      @Override public boolean isWallClockBased() { return true; }
    };
  }
}
