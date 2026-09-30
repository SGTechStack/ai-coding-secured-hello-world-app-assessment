package sg.securedhello.security.lockout;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import sg.securedhello.security.lockout.LockoutCounter.Outcome;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.user.DeviceLockState;
import sg.securedhello.user.PasswordLockoutState;

/** The two counters' transitions (ADR-011; ADR-012; ADR-013), on fixed instants. */
class LockoutCounterTest {

    private static final Duration WINDOW = Duration.ofMinutes(20);
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final PasswordLockoutState FRESH = new PasswordLockoutState(0, null, null, 0, null);

    private final LockoutCounter counter = new LockoutCounter(new LockoutLadder(5, WINDOW, 10,
            List.of(Duration.ofMinutes(20), Duration.ofMinutes(40), Duration.ofMinutes(60)), 5, 100, 50));

    private static PasswordLockoutState state(int windowed, Instant lastFailedAt, Instant lockedUntil, int sinceSuccess,
            Instant disabledAt) {
        return new PasswordLockoutState(windowed, lastFailedAt, lockedUntil, sinceSuccess, disabledAt);
    }

    @Test
    void aFirstFailureCountsOneOnBothCounters() {
        Outcome outcome = counter.failure(FRESH, T0);
        assertThat(outcome.state()).isEqualTo(state(1, T0, null, 1, null));
        assertThat(outcome.lockedFor()).isNull();
        assertThat(outcome.locked()).isFalse();
        assertThat(outcome.lockCleared()).isFalse();
        assertThat(outcome.alerted()).isFalse();
        assertThat(outcome.disabled()).isFalse();
        assertThat(outcome.changed(FRESH)).isTrue();
    }

    @Test
    void aFailureInsideTheWindowIncrements() {
        PasswordLockoutState before = state(2, T0, null, 7, null);
        Instant now = T0.plus(WINDOW).minusNanos(1000);
        assertThat(counter.failure(before, now).state()).isEqualTo(state(3, now, null, 8, null));
    }

    @Test
    void aFailureAWindowOrMoreAfterTheLastRestartsTheWindowedCounterOnly() {
        PasswordLockoutState before = state(4, T0, null, 7, null);
        assertThat(counter.failure(before, T0.plus(WINDOW)).state()).isEqualTo(state(1, T0.plus(WINDOW), null, 8, null));
        assertThat(counter.failure(before, T0.plus(Duration.ofDays(90))).state().failedLoginAttempts()).isEqualTo(1);
    }

    @Test
    void theFifthFailureInsideTheWindowLocksForTheFirstRung() {
        Outcome outcome = counter.failure(state(4, T0, null, 4, null), T0.plusSeconds(60));
        assertThat(outcome.locked()).isTrue();
        assertThat(outcome.lockedFor()).isEqualTo(Duration.ofMinutes(20));
        assertThat(outcome.state()).isEqualTo(state(5, T0.plusSeconds(60), T0.plusSeconds(60).plus(Duration.ofMinutes(20)),
                5, null));
    }

    @Test
    void theRungIsReadFromTheCapCounter() {
        // The 6th lock: 30 failures since success, the windowed counter at its 5th.
        Outcome sixth = counter.failure(state(4, T0, null, 29, null), T0.plusSeconds(1));
        assertThat(sixth.lockedFor()).isEqualTo(Duration.ofMinutes(40));
        Outcome eleventh = counter.failure(state(4, T0, null, 54, null), T0.plusSeconds(1));
        assertThat(eleventh.lockedFor()).isEqualTo(Duration.ofMinutes(60));
    }

    @Test
    @Proves("T-LCK-021")
    void theTenthFailureSinceSuccessLocksEvenWhenItIsFirstInItsWindow() {
        Instant now = T0.plus(WINDOW);
        Outcome ninth = counter.failure(state(4, T0, null, 8, null), now);
        assertThat(ninth.locked()).as("the ninth, first in its window").isFalse();

        Outcome tenth = counter.failure(state(4, T0, null, 9, null), now);
        assertThat(tenth.locked()).isTrue();
        assertThat(tenth.lockedFor()).as("the rung of the second lock").isEqualTo(Duration.ofMinutes(20));
        assertThat(tenth.state()).isEqualTo(state(1, now, now.plus(Duration.ofMinutes(20)), 10, null));

        assertThat(counter.failure(state(1, T0, null, 10, null), now).locked()).as("the eleventh").isFalse();
        Outcome thirtieth = counter.failure(state(1, T0, null, 29, null), now);
        assertThat(thirtieth.lockedFor()).as("every fifth after it, on the ladder").isEqualTo(Duration.ofMinutes(40));
    }

    @Test
    void theFirstFailureAfterALockLiftsClearsItAndStartsAFreshCount() {
        Instant lockedUntil = T0.plus(Duration.ofMinutes(20));
        PasswordLockoutState locked = state(5, T0, lockedUntil, 5, null);
        Outcome outcome = counter.failure(locked, lockedUntil);
        assertThat(outcome.lockCleared()).isTrue();
        assertThat(outcome.locked()).isFalse();
        assertThat(outcome.state()).isEqualTo(state(1, lockedUntil, null, 6, null));
    }

    @Test
    void aFailureWhileLockedOrDisabledIsNotCounted() {
        Instant lockedUntil = T0.plus(Duration.ofMinutes(20));
        PasswordLockoutState locked = state(5, T0, lockedUntil, 5, null);
        Outcome whileLocked = counter.failure(locked, lockedUntil.minusNanos(1000));
        assertThat(whileLocked.state()).isEqualTo(locked);
        assertThat(whileLocked.changed(locked)).isFalse();
        assertThat(whileLocked.lockCleared()).isFalse();
        assertThat(whileLocked.locked()).isFalse();

        PasswordLockoutState disabled = state(1, T0, null, 100, T0);
        Outcome whileDisabled = counter.failure(disabled, T0.plus(Duration.ofDays(1)));
        assertThat(whileDisabled.state()).isEqualTo(disabled);
        assertThat(whileDisabled.disabled()).isFalse();
    }

    @Test
    void theFiftiethFailureRaisesTheAlertOnce() {
        assertThat(counter.failure(state(0, null, null, 48, null), T0).alerted()).isFalse();
        assertThat(counter.failure(state(0, null, null, 49, null), T0).alerted()).isTrue();
        assertThat(counter.failure(state(0, null, null, 50, null), T0).alerted()).isFalse();
    }

    @Test
    void theHundredthFailureDisablesThePasswordInsteadOfLocking() {
        Outcome ninetyNinth = counter.failure(state(3, T0, null, 98, null), T0.plusSeconds(1));
        assertThat(ninetyNinth.disabled()).isFalse();
        assertThat(ninetyNinth.state().passwordDisabledAt()).isNull();

        Instant now = T0.plusSeconds(2);
        Outcome hundredth = counter.failure(state(4, T0, null, 99, null), now);
        assertThat(hundredth.disabled()).isTrue();
        assertThat(hundredth.locked()).isFalse();
        assertThat(hundredth.state()).isEqualTo(state(5, now, null, 100, now));
    }

    @Test
    void aSuccessResetsBothCountersAndKeepsTheCap() {
        PasswordLockoutState before = state(3, T0, null, 42, null);
        Outcome outcome = counter.success(before);
        assertThat(outcome.state()).isEqualTo(FRESH);
        assertThat(outcome.lockCleared()).isFalse();
        assertThat(outcome.locked()).isFalse();
        assertThat(outcome.alerted()).isFalse();
        assertThat(outcome.disabled()).isFalse();
        // Only rebinding clears the cap (ADR-013); a success never reaches a capped account, and would not clear it.
        assertThat(counter.success(state(0, null, null, 0, T0)).state().passwordDisabledAt()).isEqualTo(T0);
    }

    @Test
    void theFirstSuccessAfterALiftedLockClearsIt() {
        Outcome outcome = counter.success(state(5, T0, T0.plus(WINDOW), 5, null));
        assertThat(outcome.lockCleared()).isTrue();
        assertThat(outcome.state()).isEqualTo(FRESH);
    }

    @Test
    void aSuccessOnAFreshAccountChangesNothing() {
        assertThat(counter.success(FRESH).changed(FRESH)).isFalse();
    }

    private static final DeviceLockState NEW_DEVICE = DeviceLockState.CLEAR;

    @Test
    @Proves("T-LCK-025")
    void aDeviceFailureCountsInItsOwnLaneAndOnTheCapButNotInTheUntrustedLane() {
        PasswordLockoutState account = state(3, T0, null, 7, null);
        Outcome outcome = counter.failure(account, NEW_DEVICE, T0.plusSeconds(1));
        assertThat(outcome.trusted()).isTrue();
        assertThat(outcome.device()).isEqualTo(new DeviceLockState(1, T0.plusSeconds(1), null, 1));
        assertThat(outcome.state()).isEqualTo(state(3, T0, null, 8, null));
        assertThat(outcome.locked()).isFalse();
        assertThat(outcome.alerted()).isFalse();
    }

    @Test
    @Proves("T-LCK-025")
    void theDevicesFifthFailureInsideTheWindowLocksTheDeviceForTheRungOfItsOwnCount() {
        // The cap counter is far up the ladder; the device's own count picks its rung.
        Outcome outcome = counter.failure(state(0, null, null, 54, null), new DeviceLockState(4, T0, null, 4),
                T0.plusSeconds(60));
        assertThat(outcome.lockedFor()).isEqualTo(Duration.ofMinutes(20));
        assertThat(outcome.device().lockedUntil()).isEqualTo(T0.plusSeconds(60).plus(Duration.ofMinutes(20)));
        assertThat(outcome.state().lockedUntil()).as("the untrusted lane stays unlocked").isNull();
        assertThat(outcome.lockCleared()).isFalse();
        Outcome sixth = counter.failure(FRESH, new DeviceLockState(4, T0, null, 29), T0.plusSeconds(1));
        assertThat(sixth.lockedFor()).as("the sixth device lock").isEqualTo(Duration.ofMinutes(40));
    }

    @Test
    @Proves("T-LCK-025")
    void aFailureOnALockedDeviceIsNotCountedButOneOnALockedUntrustedLaneIs() {
        DeviceLockState locked = new DeviceLockState(5, T0, T0.plusSeconds(60), 5);
        PasswordLockoutState account = state(1, T0, null, 5, null);
        Outcome during = counter.failure(account, locked, T0.plusSeconds(1));
        assertThat(during.device()).isEqualTo(locked);
        assertThat(during.state()).isEqualTo(account);
        assertThat(during.lockCleared()).isFalse();

        PasswordLockoutState untrustedLocked = state(5, T0, T0.plusSeconds(60), 5, null);
        Outcome counted = counter.failure(untrustedLocked, NEW_DEVICE, T0.plusSeconds(1));
        assertThat(counted.state().consecutiveFailuresSinceSuccess()).isEqualTo(6);
        assertThat(counted.state().lockedUntil()).isEqualTo(T0.plusSeconds(60));

        Outcome lifted = counter.failure(account, locked, T0.plusSeconds(60));
        assertThat(lifted.lockCleared()).as("the device's lifted lock is cleared").isTrue();
        assertThat(lifted.device().consecutiveFailures()).isEqualTo(6);
    }

    @Test
    @Proves("T-LCK-029")
    void aDeviceFailureAtTheCapDisablesThePasswordAndRaisesTheAlertOnItsTransition() {
        Outcome disabled = counter.failure(state(0, null, null, 99, null), new DeviceLockState(4, T0, null, 4),
                T0.plusSeconds(1));
        assertThat(disabled.disabled()).isTrue();
        assertThat(disabled.locked()).as("the disable outranks a lock").isFalse();
        assertThat(disabled.state().passwordDisabledAt()).isEqualTo(T0.plusSeconds(1));
        assertThat(counter.failure(state(0, null, null, 49, null), NEW_DEVICE, T0).alerted()).isTrue();
        PasswordLockoutState capped = state(0, null, null, 100, T0);
        assertThat(counter.failure(capped, NEW_DEVICE, T0.plusSeconds(1)).state()).isEqualTo(capped);
    }

    @Test
    @Proves("T-LCK-028")
    void aTrustedSuccessResetsTheCapTheUntrustedCounterAndTheDeviceButKeepsAnUntrustedLock() {
        Instant until = T0.plusSeconds(600);
        Outcome outcome = counter.success(state(4, T0, until, 42, null), new DeviceLockState(2, T0, T0, 3));
        assertThat(outcome.state()).isEqualTo(state(0, null, until, 0, null));
        assertThat(outcome.device()).isEqualTo(DeviceLockState.CLEAR);
        assertThat(outcome.lockCleared()).as("the device's lifted lock").isTrue();
        assertThat(counter.success(FRESH, NEW_DEVICE).lockCleared()).isFalse();
        assertThat(counter.success(state(0, null, null, 100, T0), NEW_DEVICE).state().passwordDisabledAt())
                .isEqualTo(T0);
    }

    @Test
    void theDeviceLadderIsItsOwn() {
        List<Duration> rungs = List.of(Duration.ofMinutes(20), Duration.ofMinutes(40), Duration.ofMinutes(60));
        LockoutCounter lanes = new LockoutCounter(new LockoutLadder(5, WINDOW, 10, rungs, 5, 100, 50),
                new LockoutLadder(2, WINDOW, 10, rungs, 5, 100, 50));
        assertThat(lanes.failure(FRESH, new DeviceLockState(1, T0, null, 1), T0.plusSeconds(1)).locked()).isTrue();
        assertThat(lanes.failure(state(1, T0, null, 1, null), T0.plusSeconds(1)).locked()).isFalse();
    }
}
