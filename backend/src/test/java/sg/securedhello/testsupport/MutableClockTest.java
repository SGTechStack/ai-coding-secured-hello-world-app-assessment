package sg.securedhello.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

class MutableClockTest {

    @Test
    void startsAtRealTimeInUtc() {
        MutableClock clock = MutableClock.startingNow();

        assertThat(clock.instant()).isCloseTo(Clock.systemUTC().instant(), within(5, ChronoUnit.SECONDS));
        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void standsStillUntilAdvanced() {
        MutableClock clock = MutableClock.startingAt(Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(clock.instant()).isEqualTo(clock.instant());
    }

    @Test
    void advanceMovesForwardAndReturnsTheNewInstant() {
        MutableClock clock = MutableClock.startingAt(Instant.parse("2026-01-01T00:00:00Z"));

        Instant after = clock.advance(Duration.ofMinutes(15));

        assertThat(after).isEqualTo(Instant.parse("2026-01-01T00:15:00Z"));
        assertThat(clock.instant()).isEqualTo(after);
        assertThat(clock.millis()).isEqualTo(after.toEpochMilli());
    }

    @Test
    void refusesToMoveBackwards() {
        MutableClock clock = MutableClock.startingAt(Instant.parse("2026-01-01T00:00:00Z"));

        assertThatIllegalArgumentException().isThrownBy(() -> clock.advance(Duration.ofNanos(-1)));
        assertThat(clock.instant()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void zonedViewSharesTheSameTimeline() {
        MutableClock clock = MutableClock.startingAt(Instant.parse("2026-01-01T00:00:00Z"));
        Clock singapore = clock.withZone(ZoneId.of("Asia/Singapore"));

        clock.advance(Duration.ofHours(1));

        assertThat(singapore.getZone()).isEqualTo(ZoneId.of("Asia/Singapore"));
        assertThat(singapore.instant()).isEqualTo(Instant.parse("2026-01-01T01:00:00Z"));
    }
}
