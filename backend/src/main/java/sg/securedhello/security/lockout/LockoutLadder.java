package sg.securedhello.security.lockout;

import java.time.Duration;
import java.util.List;

/**
 * The escalating lockout ladder and its startup floor (ADR-011). Pure arithmetic, no state.
 *
 * <p>The n-th lock lasts {@code rungs[(n - 1) / cyclesPerRung]}, and every lock past the last rung lasts the last rung.
 * The lock number is read from the cap counter, never from the windowed counter, which resets on staleness and would
 * never climb (ADR-011): the lock reached at {@code c} failures since success is {@code ceil(c / threshold)}.
 *
 * <p>The floor: attempts during a lock do not count, so a steady attacker reaches the cap after
 * {@code floor((cap - 1) / threshold)} locks and the alert after {@code floor((alert - 1) / threshold)}. The time to
 * the disable is the sum of the first set of locks, and the warning is what the alert leaves of it. A ladder that
 * disables in under {@value #FLOOR_TO_DISABLE_MINUTES} minutes or warns for under {@value #FLOOR_WARNING_MINUTES} is
 * refused: deployers may make it slower, never faster. Checking the rungs alone would not do, because raising the
 * threshold halves the lock count while every rung still passes.
 */
public final class LockoutLadder {

    static final long FLOOR_TO_DISABLE_MINUTES = 840;
    static final long FLOOR_WARNING_MINUTES = 580;

    /** The least time from the first failure to the password disable, under a steady attack. */
    static final Duration FLOOR_TO_DISABLE = Duration.ofMinutes(FLOOR_TO_DISABLE_MINUTES);

    /** The least time between the alert and the disable. */
    static final Duration FLOOR_WARNING = Duration.ofMinutes(FLOOR_WARNING_MINUTES);

    private final int threshold;
    private final List<Duration> rungs;
    private final int cyclesPerRung;
    private final int cap;
    private final int alertThreshold;

    /**
     * @param threshold      failures inside the observation window that lock the account
     * @param rungs          the lock durations, shortest first
     * @param cyclesPerRung  how many locks each rung lasts for
     * @param cap            failures since success that disable the password (ADR-013)
     * @param alertThreshold failures since success that raise the alert
     */
    public LockoutLadder(int threshold, List<Duration> rungs, int cyclesPerRung, int cap, int alertThreshold) {
        if (threshold < 1 || rungs.isEmpty() || cyclesPerRung < 1 || cap < 1 || alertThreshold < 1) {
            throw new IllegalArgumentException("The lockout ladder needs positive counts and at least one rung");
        }
        this.threshold = threshold;
        this.rungs = List.copyOf(rungs);
        this.cyclesPerRung = cyclesPerRung;
        this.cap = cap;
        this.alertThreshold = alertThreshold;
    }

    public int threshold() {
        return threshold;
    }

    public int cap() {
        return cap;
    }

    public int alertThreshold() {
        return alertThreshold;
    }

    /** Which lock {@code consecutiveFailures} failures since success reach, counting from 1. */
    int lockNumber(int consecutiveFailures) {
        return Math.ceilDiv(consecutiveFailures, threshold);
    }

    /** How long the {@code lockNumber}-th lock lasts. */
    Duration lockDuration(int lockNumber) {
        return rungs.get(Math.min((lockNumber - 1) / cyclesPerRung, rungs.size() - 1));
    }

    int locksBeforeCap() {
        return (cap - 1) / threshold;
    }

    int locksBeforeAlert() {
        return (alertThreshold - 1) / threshold;
    }

    /** The time a steady attack takes to reach the disable: the first {@link #locksBeforeCap} locks. */
    Duration timeToDisable() {
        return lockTime(locksBeforeCap());
    }

    /** The time a steady attack takes to reach the alert. */
    Duration timeToAlert() {
        return lockTime(locksBeforeAlert());
    }

    /** The warning the alert gives before the disable. */
    Duration warning() {
        return timeToDisable().minus(timeToAlert());
    }

    /**
     * @throws IllegalArgumentException if the disable is under {@value #FLOOR_TO_DISABLE_MINUTES} minutes away or the
     *                                  warning is under {@value #FLOOR_WARNING_MINUTES}
     */
    void requireFloor() {
        if (timeToDisable().compareTo(FLOOR_TO_DISABLE) < 0) {
            throw new IllegalArgumentException("The lockout ladder disables a password after "
                    + timeToDisable().toMinutes() + " minutes of steady attack; the floor is "
                    + FLOOR_TO_DISABLE_MINUTES + " (ADR-011)");
        }
        if (warning().compareTo(FLOOR_WARNING) < 0) {
            throw new IllegalArgumentException("The lockout ladder leaves " + warning().toMinutes()
                    + " minutes between the alert and the disable; the floor is " + FLOOR_WARNING_MINUTES
                    + " (ADR-011)");
        }
    }

    /**
     * @throws IllegalArgumentException if a rung is shorter than {@code window}: the next failure after that lock lifts
     *                                  would still be inside the window and re-lock at once (ADR-012)
     */
    void requireRungsOfAtLeast(Duration window) {
        for (Duration rung : rungs) {
            if (rung.compareTo(window) < 0) {
                throw new IllegalArgumentException("A lockout rung of " + rung.toMinutes()
                        + " minutes is shorter than the observation window (ADR-012)");
            }
        }
    }

    private Duration lockTime(int locks) {
        Duration total = Duration.ZERO;
        for (int lock = 1; lock <= locks; lock++) {
            total = total.plus(lockDuration(lock));
        }
        return total;
    }
}
