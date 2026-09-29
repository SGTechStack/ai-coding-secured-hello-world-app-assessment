package sg.securedhello.session.shedding;

import java.time.Duration;
import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * The shed decision of ADR-041, with no I/O: whether a shed episode is running, given the live session-row count
 * {@code N} and the free bytes on the database volume.
 *
 * <table>
 *   <caption>The two lines</caption>
 *   <tr><th>Line</th><th>Trips when</th><th>Clears when</th></tr>
 *   <tr><td>Count</td><td>{@code N ≥ N_max}</td><td>{@code N ≤ 0.9 × N_max}</td></tr>
 *   <tr><td>Free space</td><td>{@code free < k × N × b + floor}</td>
 *       <td>{@code free ≥ 1.1 × (k × N × b + floor)}</td></tr>
 * </table>
 *
 * <p>An episode clears only when both lines are in their clear bands and it has lasted at least {@link #DWELL}. A
 * check that cannot be evaluated sheds. {@code N_max}, {@code k} and {@code b} are planning values, not settings: the
 * lever that raises {@code N_max} is measuring {@code b} further, not configuring it higher (ADR-041; R-RL-014). Not
 * thread-safe; {@link AnonymousSessionShedding} serialises every call.
 */
final class ShedEpisode {

    /** {@code N_max}: the measured range of the per-row cost, and so the count cap. */
    static final long N_MAX = 100_000;

    /** {@code k}: the reserve's multiplier, covering the transient during a bulk cleanup {@code DELETE}. */
    static final double K = 1.5;

    /** {@code b}: bytes per session row, read after {@code CHECKPOINT} at 100,000 rows. */
    static final long ROW_BYTES = 17_000;

    /** The shortest episode: it bounds flapping, which the hysteresis bands only reduce. */
    static final Duration DWELL = Duration.ofSeconds(60);

    /** {@code k × b}: the reserve's bytes per live row. */
    private static final long RESERVE_PER_ROW = Math.round(K * ROW_BYTES);

    private final long floor;

    private @Nullable Instant startedAt;

    /** @param floor the free-space line's constant term, in bytes */
    ShedEpisode(long floor) {
        this.floor = floor;
    }

    /** Whether an episode is running, as last decided. */
    boolean shedding() {
        return startedAt != null;
    }

    /** Decides at {@code now} from {@code rows} live session rows and {@code free} usable bytes; true sheds. */
    boolean decide(Instant now, long rows, long free) {
        long reserve = rows * RESERVE_PER_ROW + floor;
        if (startedAt == null) {
            if (rows >= N_MAX || free < reserve) {
                startedAt = now;
            }
        } else if (rows * 10 <= N_MAX * 9 && free - reserve >= divideRoundingUp(reserve, 10)
                && !now.isBefore(startedAt.plus(DWELL))) {
            startedAt = null;
        }
        return shedding();
    }

    /** The check could not be evaluated: shed, starting an episode if none is running. */
    boolean unevaluable(Instant now) {
        if (startedAt == null) {
            startedAt = now;
        }
        return true;
    }

    private static long divideRoundingUp(long dividend, long divisor) {
        return (dividend + divisor - 1) / divisor;
    }
}
