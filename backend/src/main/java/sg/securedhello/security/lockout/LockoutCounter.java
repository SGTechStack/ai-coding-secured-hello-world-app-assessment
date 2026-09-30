package sg.securedhello.security.lockout;

import java.time.Duration;
import java.time.Instant;

import org.jspecify.annotations.Nullable;

import sg.securedhello.user.PasswordLockoutState;

/**
 * The password lockout's two counters and their transitions (ADR-011; ADR-012; ADR-013). Pure: it takes the account's
 * {@link PasswordLockoutState} and the time, and returns the next state and what happened on the way.
 *
 * <p>On a wrong password:
 * <ul>
 *   <li>the windowed counter increments if the previous failure is less than the observation window old, and
 *       otherwise restarts at 1 ({@link ObservationWindow}; ADR-012). Because every rung is at least the window, the
 *       first failure after a lock lifts always restarts it, so a lifted lock gives the user a full count again;</li>
 *   <li>the cap counter increments, with no window;</li>
 *   <li>at the cap the password is disabled, which outranks a lock; when either lock rule fires
 *       ({@link LockoutLadder#locksAt}: the threshold inside the window, or the consecutive threshold since success and
 *       every threshold-th failure after it) the account locks for the rung the cap counter has reached (ADR-011); at
 *       the alert threshold the alert is raised. Each fires on its transition only.</li>
 * </ul>
 * A failure that finds the account locked or disabled is not counted: it raced past the pre-authentication check, and
 * attempts during a lock never advance the cap (ADR-013).
 *
 * <p>On a correct password both counters return to 0. The cap's disabled state is kept: only rebinding clears it.
 *
 * <p>A lock that has lifted is cleared, and reported, by the first outcome that finds it lifted: the lock ends with no
 * scheduler, so this is where its end is observed.
 */
public final class LockoutCounter {

    private final LockoutLadder ladder;

    /**
     * @param ladder the thresholds, the observation window, the rungs, the cap and the alert
     */
    public LockoutCounter(LockoutLadder ladder) {
        this.ladder = ladder;
    }

    /** A wrong password for the account at {@code now}. */
    public Outcome failure(PasswordLockoutState state, Instant now) {
        if (state.passwordDisabled() || state.lockedAt(now)) {
            return new Outcome(state, false, null, false, false);
        }
        int windowed = ObservationWindow.count(state.failedLoginAttempts(), state.lastFailedAt(), now,
                ladder.window());
        int sinceSuccess = state.consecutiveFailuresSinceSuccess() + 1;
        boolean disabled = sinceSuccess >= ladder.cap();
        Duration lockedFor = !disabled && ladder.locksAt(sinceSuccess, windowed)
                ? ladder.lockDuration(ladder.lockNumber(sinceSuccess))
                : null;
        PasswordLockoutState next = new PasswordLockoutState(windowed, now,
                lockedFor == null ? null : now.plus(lockedFor), sinceSuccess, disabled ? now : null);
        return new Outcome(next, state.lockedUntil() != null, lockedFor, sinceSuccess == ladder.alertThreshold(),
                disabled);
    }

    /** A correct password for the account, which the pre-authentication checks found neither locked nor disabled. */
    public Outcome success(PasswordLockoutState state) {
        PasswordLockoutState next = new PasswordLockoutState(0, null, null, 0, state.passwordDisabledAt());
        return new Outcome(next, state.lockedUntil() != null, null, false, false);
    }

    /**
     * The next state, and the transitions that produced it.
     *
     * @param state       the state to store
     * @param lockCleared a lock that had lifted was cleared
     * @param lockedFor   the new lock's duration, or {@code null} if this outcome did not lock
     * @param alerted     the cap counter reached the alert threshold
     * @param disabled    the cap counter reached the cap and the password is now disabled
     */
    public record Outcome(PasswordLockoutState state, boolean lockCleared, @Nullable Duration lockedFor,
            boolean alerted, boolean disabled) {

        /** Whether this outcome locked the account. */
        public boolean locked() {
            return lockedFor != null;
        }

        /** Whether the state differs from {@code before}, so it needs writing. */
        public boolean changed(PasswordLockoutState before) {
            return !state.equals(before);
        }
    }
}
