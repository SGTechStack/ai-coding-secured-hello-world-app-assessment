package sg.securedhello.security.lockout;

import java.time.Duration;
import java.time.Instant;

import org.jspecify.annotations.Nullable;

import sg.securedhello.user.DeviceLockState;
import sg.securedhello.user.PasswordLockoutState;

/**
 * The password lockout's counters and their transitions, in two lanes (ADR-011; ADR-012; ADR-013; ADR-075). Pure: it
 * takes the account's {@link PasswordLockoutState}, a trusted device's {@link DeviceLockState} for the device lane,
 * and the time, and returns the next states and what happened on the way.
 *
 * <p>A sign-in with no valid device cookie for its username counts in the account's <b>untrusted lane</b>; one with a
 * valid cookie counts in that device's own <b>device lane</b>. Each lane has its own windowed counter and lock, on the
 * same ladder. The NIST cap counter is the account's, and counts every failure in every lane.
 *
 * <p>On a wrong password:
 * <ul>
 *   <li>the lane's windowed counter increments if the previous failure is less than the observation window old, and
 *       otherwise restarts at 1 ({@link ObservationWindow}; ADR-012). Because every rung is at least the window, the
 *       first failure after a lock lifts always restarts it, so a lifted lock gives the user a full count again;</li>
 *   <li>the cap counter increments, with no window;</li>
 *   <li>at the cap the password is disabled, which outranks a lock; when either lock rule fires
 *       ({@link LockoutLadder#locksAt}: the threshold inside the window, or the consecutive threshold since success and
 *       every threshold-th failure after it) the lane locks for the rung its count since success has reached: the cap
 *       counter for the untrusted lane (ADR-011), the device's own count for a device lane; at the alert threshold
 *       the alert is raised. Each fires on its transition only.</li>
 * </ul>
 * A failure that finds the password disabled or its own lane locked is not counted: it raced past the lock check, and
 * attempts during a lock never advance the cap (ADR-013). A device-lane failure while the untrusted lane is locked is
 * counted: that lock is not the device's.
 *
 * <p>On a correct password the lane's counters and the cap counter return to 0 (NIST SP 800-63B-4 §3.2.2). A trusted
 * device's success also resets the untrusted lane's windowed counter, but leaves an untrusted lock in force until it
 * lifts. The cap's disabled state is kept: only rebinding clears it.
 *
 * <p>A lock that has lifted is cleared, and reported, by the first outcome in its lane that finds it lifted: the lock
 * ends with no scheduler, so this is where its end is observed.
 */
public final class LockoutCounter {

    private final LockoutLadder ladder;
    private final LockoutLadder deviceLadder;

    /**
     * @param ladder the thresholds, the observation window, the rungs, the cap and the alert, for both lanes
     */
    public LockoutCounter(LockoutLadder ladder) {
        this(ladder, ladder);
    }

    /**
     * @param ladder       the untrusted lane's ladder, whose cap and alert are the account's
     * @param deviceLadder a device lane's ladder: its threshold, window and rungs; its cap and alert are not read
     */
    public LockoutCounter(LockoutLadder ladder, LockoutLadder deviceLadder) {
        this.ladder = ladder;
        this.deviceLadder = deviceLadder;
    }

    /** A wrong password in the account's untrusted lane at {@code now}. */
    public Outcome failure(PasswordLockoutState state, Instant now) {
        if (state.passwordDisabled() || state.lockedAt(now)) {
            return new Outcome(state, null, false, null, false, false);
        }
        int windowed = ObservationWindow.count(state.failedLoginAttempts(), state.lastFailedAt(), now,
                ladder.window());
        int sinceSuccess = state.consecutiveFailuresSinceSuccess() + 1;
        boolean disabled = sinceSuccess >= ladder.cap();
        Duration lockedFor = disabled ? null : lockFor(ladder, sinceSuccess, windowed);
        PasswordLockoutState next = new PasswordLockoutState(windowed, now,
                lockedFor == null ? null : now.plus(lockedFor), sinceSuccess, disabled ? now : null);
        return new Outcome(next, null, state.lockedUntil() != null, lockedFor,
                sinceSuccess == ladder.alertThreshold(), disabled);
    }

    /** A wrong password from a trusted device, in its own lane, at {@code now}. */
    public Outcome failure(PasswordLockoutState account, DeviceLockState device, Instant now) {
        if (account.passwordDisabled() || device.lockedAt(now)) {
            return new Outcome(account, device, false, null, false, false);
        }
        int windowed = ObservationWindow.count(device.failedLoginAttempts(), device.lastFailedAt(), now,
                deviceLadder.window());
        int deviceSinceSuccess = device.consecutiveFailures() + 1;
        int sinceSuccess = account.consecutiveFailuresSinceSuccess() + 1;
        boolean disabled = sinceSuccess >= ladder.cap();
        Duration lockedFor = disabled ? null : lockFor(deviceLadder, deviceSinceSuccess, windowed);
        DeviceLockState nextDevice = new DeviceLockState(windowed, now,
                lockedFor == null ? null : now.plus(lockedFor), deviceSinceSuccess);
        PasswordLockoutState nextAccount = new PasswordLockoutState(account.failedLoginAttempts(),
                account.lastFailedAt(), account.lockedUntil(), sinceSuccess, disabled ? now : null);
        return new Outcome(nextAccount, nextDevice, device.lockedUntil() != null, lockedFor,
                sinceSuccess == ladder.alertThreshold(), disabled);
    }

    /** A correct password in the untrusted lane, which the lock check found neither locked nor disabled. */
    public Outcome success(PasswordLockoutState state) {
        PasswordLockoutState next = new PasswordLockoutState(0, null, null, 0, state.passwordDisabledAt());
        return new Outcome(next, null, state.lockedUntil() != null, null, false, false);
    }

    /**
     * A correct password from a trusted device, which the lock check found neither locked nor disabled: the device's
     * counters, the cap counter and the untrusted lane's windowed counter reset. An untrusted lock stays until it
     * lifts, and the next untrusted outcome that finds it lifted clears it.
     */
    public Outcome success(PasswordLockoutState account, DeviceLockState device) {
        PasswordLockoutState next = new PasswordLockoutState(0, null, account.lockedUntil(), 0,
                account.passwordDisabledAt());
        return new Outcome(next, DeviceLockState.CLEAR, device.lockedUntil() != null, null, false, false);
    }

    /** How long a failure locks its lane for on {@code lane}, or {@code null} if it does not lock. */
    private static @Nullable Duration lockFor(LockoutLadder lane, int sinceSuccess, int windowed) {
        return lane.locksAt(sinceSuccess, windowed) ? lane.lockDuration(lane.lockNumber(sinceSuccess)) : null;
    }

    /**
     * The next states, and the transitions that produced them.
     *
     * @param state       the account's state to store
     * @param device      the device's state to store, for a device-lane outcome; {@code null} for the untrusted lane
     * @param lockCleared a lock of this outcome's lane that had lifted was cleared
     * @param lockedFor   the new lock's duration, or {@code null} if this outcome did not lock its lane
     * @param alerted     the cap counter reached the alert threshold
     * @param disabled    the cap counter reached the cap and the password is now disabled
     */
    public record Outcome(PasswordLockoutState state, @Nullable DeviceLockState device, boolean lockCleared,
            @Nullable Duration lockedFor, boolean alerted, boolean disabled) {

        /** Whether this outcome locked its lane. */
        public boolean locked() {
            return lockedFor != null;
        }

        /** Whether this outcome is a trusted device's, in its own lane. */
        public boolean trusted() {
            return device != null;
        }

        /** Whether the account's state differs from {@code before}, so it needs writing. */
        public boolean changed(PasswordLockoutState before) {
            return !state.equals(before);
        }
    }
}
