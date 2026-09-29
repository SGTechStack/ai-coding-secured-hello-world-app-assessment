package sg.securedhello.session.shedding;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import sg.securedhello.testsupport.Proves;

/** ADR-041: the count and free-space lines, their hysteresis bands, the dwell and the fail-shed rule. */
class ShedEpisodeTest {

    private static final long FLOOR = 256L * 1024 * 1024;
    private static final long PLENTY = Long.MAX_VALUE / 2;
    private static final Instant T0 = Instant.parse("2026-09-29T00:00:00Z");

    private final ShedEpisode episode = new ShedEpisode(FLOOR);

    private static Instant at(Duration offset) {
        return T0.plus(offset);
    }

    /** The reserve at {@code rows}: k × N × b + floor. */
    private static long reserve(long rows) {
        return (long) Math.ceil(ShedEpisode.K * rows * ShedEpisode.ROW_BYTES) + FLOOR;
    }

    @Test
    @Proves("T-RL-024")
    void theCountLineTripsAtNMaxAndNotOneRowBelow() {
        assertThat(episode.decide(T0, ShedEpisode.N_MAX - 1, PLENTY)).isFalse();
        assertThat(episode.shedding()).isFalse();

        assertThat(episode.decide(T0, ShedEpisode.N_MAX, PLENTY)).isTrue();
        assertThat(episode.shedding()).isTrue();
    }

    @Test
    @Proves("T-RL-024")
    void theCountLineClearsOnlyAtNinetyPercentOnceTheDwellHasRun() {
        episode.decide(T0, ShedEpisode.N_MAX, PLENTY);
        Instant afterDwell = at(ShedEpisode.DWELL);
        long clearLine = ShedEpisode.N_MAX * 9 / 10;

        assertThat(episode.decide(afterDwell, clearLine + 1, PLENTY)).as("inside the band").isTrue();
        assertThat(episode.decide(afterDwell, clearLine, PLENTY)).as("at the clear line").isFalse();
    }

    @Test
    @Proves("T-RL-024")
    void theFreeSpaceLineTripsBelowTheReserveThatGrowsWithTheRowCount() {
        long rows = 20_000;

        assertThat(episode.decide(T0, rows, reserve(rows))).as("exactly at the reserve").isFalse();
        assertThat(episode.decide(T0, rows, reserve(rows) - 1)).as("one byte under").isTrue();
    }

    @Test
    @Proves("T-RL-024")
    void theFreeSpaceLineFallsAsRowsAreRemoved() {
        // The reserve is k × N × b + floor, not proportional to the file: a smaller N lowers the line (no latch).
        long free = reserve(10_000);
        assertThat(episode.decide(T0, 20_000, free)).isTrue();
        assertThat(episode.decide(at(ShedEpisode.DWELL), 1_000, free)).isFalse();
    }

    @Test
    @Proves("T-RL-024")
    void theFreeSpaceLineClearsOnlyAtTenPercentAboveTheReserve() {
        long rows = 5_000;
        episode.decide(T0, rows, reserve(rows) - 1);
        Instant afterDwell = at(ShedEpisode.DWELL);
        long clearLine = (long) Math.ceil(1.1 * reserve(rows));

        assertThat(episode.decide(afterDwell, rows, reserve(rows))).as("back at the trip line").isTrue();
        assertThat(episode.decide(afterDwell, rows, clearLine - 1)).as("just under the clear line").isTrue();
        assertThat(episode.decide(afterDwell, rows, clearLine)).as("at the clear line").isFalse();
    }

    @Test
    @Proves("T-RL-024")
    void theFreeSpaceClearLineRoundsUp() {
        // Reserve 9 bytes: 1.1 x 9 = 9.9, so the clear line is 10 bytes, not 9 or 11.
        ShedEpisode tiny = new ShedEpisode(9);
        tiny.decide(T0, 0, 8);
        Instant afterDwell = at(ShedEpisode.DWELL);

        assertThat(tiny.decide(afterDwell, 0, 9)).isTrue();
        assertThat(tiny.decide(afterDwell, 0, 10)).isFalse();
    }

    @Test
    @Proves("T-RL-024")
    void theDwellHoldsAClearForSixtySeconds() {
        episode.decide(T0, ShedEpisode.N_MAX, PLENTY);

        assertThat(episode.decide(at(ShedEpisode.DWELL.minusMillis(1)), 0, PLENTY)).as("inside the dwell").isTrue();
        assertThat(episode.decide(at(ShedEpisode.DWELL), 0, PLENTY)).as("dwell over").isFalse();
    }

    @Test
    @Proves("T-RL-024")
    void aCheckThatCannotBeEvaluatedShedsAndStartsAnEpisode() {
        assertThat(episode.unevaluable(T0)).isTrue();
        assertThat(episode.shedding()).isTrue();

        // The failed check started the episode, so its dwell holds whatever the next check says.
        assertThat(episode.decide(at(Duration.ofSeconds(30)), 0, PLENTY)).isTrue();
        assertThat(episode.decide(at(ShedEpisode.DWELL), 0, PLENTY)).isFalse();
    }

    @Test
    @Proves("T-RL-024")
    void aTripInsideAnEpisodeDoesNotRestartTheDwell() {
        episode.decide(T0, ShedEpisode.N_MAX, PLENTY);
        episode.decide(at(Duration.ofSeconds(59)), ShedEpisode.N_MAX, PLENTY);

        assertThat(episode.decide(at(ShedEpisode.DWELL), 0, PLENTY)).isFalse();
    }

    @Test
    void thePlanningValuesAreTheAdrs() {
        assertThat(ShedEpisode.N_MAX).isEqualTo(100_000);
        assertThat(ShedEpisode.K).isEqualTo(1.5);
        assertThat(ShedEpisode.ROW_BYTES).isEqualTo(17_000);
        assertThat(ShedEpisode.DWELL).isEqualTo(Duration.ofSeconds(60));
    }
}
