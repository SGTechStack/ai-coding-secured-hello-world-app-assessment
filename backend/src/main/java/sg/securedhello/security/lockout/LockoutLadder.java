package sg.securedhello.security.lockout;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * The escalating lockout ladder, when a failure locks, and the startup floor (ADR-011; ADR-012). Pure arithmetic, no
 * state.
 *
 * <p>A failure below the cap locks the account on either of two rules ({@link #locksAt}):
 * <ul>
 *   <li><b>the windowed rule:</b> {@code threshold} failures inside the observation window (ADR-012);</li>
 *   <li><b>the consecutive rule:</b> the {@code consecutiveThreshold}-th failure since the last success, whatever the
 *       window, and every {@code threshold}-th failure after it. The consecutive threshold is a multiple of the
 *       threshold, so this rule locks exactly where a steady attack would, and on the same rung. Past that point the window no longer forgives, so an
 *       attacker who paces failures under the windowed rule is locked on the same cadence as a steady one (ADR-011
 *       amendment of 2026-09-29).</li>
 * </ul>
 *
 * <p>The n-th lock lasts {@code rungs[(n - 1) / cyclesPerRung]}, and every lock past the last rung lasts the last rung.
 * The lock number is read from the cap counter, never from the windowed counter, which resets on staleness and would
 * never climb (ADR-011): the lock reached at {@code c} failures since success is {@code ceil(c / threshold)}.
 *
 * <p>The floor models the fastest attack of any pacing, not only the steady one ({@link #fastestToCap}). Attempts
 * during a lock do not count, and before each failure the attacker may wait out the window so the windowed counter
 * restarts. The time to the disable is that attack's time from its first failure to the cap, and the warning is its
 * time from the alert's failure to the cap. A configuration that disables in under {@value #FLOOR_TO_DISABLE_MINUTES}
 * minutes or warns for under {@value #FLOOR_WARNING_MINUTES} is refused: deployers may make it slower, never faster.
 * Checking the rungs alone would not do: raising the threshold halves the lock count while every rung still passes,
 * and raising the consecutive threshold lets a paced attack skip locks.
 */
public final class LockoutLadder {

    static final long FLOOR_TO_DISABLE_MINUTES = 840;
    static final long FLOOR_WARNING_MINUTES = 580;

    /** The least time from the first failure to the password disable, under any pacing. */
    static final Duration FLOOR_TO_DISABLE = Duration.ofMinutes(FLOOR_TO_DISABLE_MINUTES);

    /** The least time between the alert and the disable. */
    static final Duration FLOOR_WARNING = Duration.ofMinutes(FLOOR_WARNING_MINUTES);

    private static final long UNREACHED = Long.MAX_VALUE;

    private final int threshold;
    private final Duration window;
    private final int consecutiveThreshold;
    private final List<Duration> rungs;
    private final int cyclesPerRung;
    private final int cap;
    private final int alertThreshold;

    /**
     * @param threshold            failures inside the observation window that lock the account
     * @param window               the observation window (ADR-012)
     * @param consecutiveThreshold failures since the last success that lock the account whatever the window
     * @param rungs                the lock durations, shortest first
     * @param cyclesPerRung        how many locks each rung lasts for
     * @param cap                  failures since success that disable the password (ADR-013)
     * @param alertThreshold       failures since success that raise the alert
     */
    public LockoutLadder(int threshold, Duration window, int consecutiveThreshold, List<Duration> rungs,
            int cyclesPerRung, int cap, int alertThreshold) {
        if (threshold < 1 || consecutiveThreshold < 1 || rungs.isEmpty() || cyclesPerRung < 1 || cap < 1
                || alertThreshold < 1) {
            throw new IllegalArgumentException("The lockout ladder needs positive counts and at least one rung");
        }
        if (consecutiveThreshold % threshold != 0) {
            throw new IllegalArgumentException("The consecutive threshold " + consecutiveThreshold
                    + " must be a multiple of the lock threshold " + threshold
                    + ", so both rules lock on one cadence (ADR-011)");
        }
        this.threshold = threshold;
        this.window = window;
        this.consecutiveThreshold = consecutiveThreshold;
        this.rungs = List.copyOf(rungs);
        this.cyclesPerRung = cyclesPerRung;
        this.cap = cap;
        this.alertThreshold = alertThreshold;
    }

    public int threshold() {
        return threshold;
    }

    /** The observation window (ADR-012). */
    public Duration window() {
        return window;
    }

    public int consecutiveThreshold() {
        return consecutiveThreshold;
    }

    public int cap() {
        return cap;
    }

    public int alertThreshold() {
        return alertThreshold;
    }

    /**
     * Whether a failure below the cap locks the account.
     *
     * @param sinceSuccess the failures since the last success, this one included
     * @param windowed     the failures inside the observation window, this one included
     */
    boolean locksAt(int sinceSuccess, int windowed) {
        return windowed >= threshold || sinceSuccess >= consecutiveThreshold && sinceSuccess % threshold == 0;
    }

    /** Which lock {@code consecutiveFailures} failures since success reach, counting from 1. */
    int lockNumber(int consecutiveFailures) {
        return Math.ceilDiv(consecutiveFailures, threshold);
    }

    /** How long the {@code lockNumber}-th lock lasts. */
    Duration lockDuration(int lockNumber) {
        return rungs.get(Math.min((lockNumber - 1) / cyclesPerRung, rungs.size() - 1));
    }

    /** The least time the fastest attack takes from its first failure to the disable. */
    Duration timeToDisable() {
        long[] start = unreached();
        start[0] = 0;
        return Duration.ofMillis(fastestToCap(0, start));
    }

    /**
     * The least time the fastest attack leaves between the alert's failure and the disable, whatever count in the
     * window the alert's failure lands on. None if the alert is not below the cap.
     */
    Duration warning() {
        if (alertThreshold >= cap) {
            return Duration.ZERO;
        }
        long[] afterAlert = unreached();
        for (int windowed = 1; windowed <= threshold; windowed++) {
            fail(alertThreshold, windowed, 0, afterAlert);
        }
        return Duration.ofMillis(fastestToCap(alertThreshold, afterAlert));
    }

    /**
     * @throws IllegalArgumentException if the disable is under {@value #FLOOR_TO_DISABLE_MINUTES} minutes away or the
     *                                  warning is under {@value #FLOOR_WARNING_MINUTES}
     */
    void requireFloor() {
        long toDisable = timeToDisable().toMinutes();
        if (toDisable < FLOOR_TO_DISABLE_MINUTES) {
            throw new IllegalArgumentException("The lockout ladder lets an attack disable a password after " + toDisable
                    + " minutes; the floor is " + FLOOR_TO_DISABLE_MINUTES + " (ADR-011)");
        }
        long warning = warning().toMinutes();
        if (warning < FLOOR_WARNING_MINUTES) {
            throw new IllegalArgumentException("The lockout ladder leaves " + warning
                    + " minutes between the alert and the disable; the floor is " + FLOOR_WARNING_MINUTES
                    + " (ADR-011)");
        }
    }

    /**
     * @throws IllegalArgumentException if a rung is shorter than the window: the next failure after that lock lifts
     *                                  would still be inside the window and re-lock at once (ADR-012)
     */
    void requireRungsOfAtLeastTheWindow() {
        for (Duration rung : rungs) {
            if (rung.compareTo(window) < 0) {
                throw new IllegalArgumentException("A lockout rung of " + rung.toMinutes()
                        + " minutes is shorter than the observation window (ADR-012)");
            }
        }
    }

    /**
     * The fastest attack onward from failure number {@code made}, by dynamic programming over the failures.
     * {@code reached[w]} is the least time by which {@code made} failures are in with {@code w} of them counting in the
     * current window (0: none, after a lock or a wait). Before each next failure the attacker either fails at once or
     * first waits out the window. A lock costs its rung and restarts the window, since every rung is at least the
     * window. The cap's failure disables at once, so the attack ends when the failure before it is in.
     */
    private long fastestToCap(int made, long[] reached) {
        long[] best = reached;
        for (int failure = made + 1; failure < cap; failure++) {
            long[] next = unreached();
            for (int windowed = 0; windowed < threshold; windowed++) {
                if (best[windowed] != UNREACHED) {
                    fail(failure, windowed + 1, best[windowed], next);
                    fail(failure, 1, best[windowed] + window.toMillis(), next);
                }
            }
            best = next;
        }
        return Arrays.stream(best).min().orElseThrow();
    }

    /** Records failure number {@code failure}, made at {@code at} as the {@code windowed}-th in its window. */
    private void fail(int failure, int windowed, long at, long[] next) {
        if (locksAt(failure, windowed)) {
            next[0] = Math.min(next[0], at + lockDuration(lockNumber(failure)).toMillis());
        } else {
            next[windowed] = Math.min(next[windowed], at);
        }
    }

    private long[] unreached() {
        long[] times = new long[threshold];
        Arrays.fill(times, UNREACHED);
        return times;
    }
}
