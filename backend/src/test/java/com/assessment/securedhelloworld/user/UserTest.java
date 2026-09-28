package com.assessment.securedhelloworld.user;

import com.assessment.securedhelloworld.auth.LockoutPolicy;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@code User}'s lockout-owning methods
 * ({@code recordFailedAttempt}, {@code recordSuccess}, {@code clearLockout},
 * {@code isLocked}), run against {@link Clock#fixed} rather than a live
 * clock or hand-shifted {@code Instant} values — no Spring context
 * needed, since all of this behaviour is a plain entity method now.
 */
class UserTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    private static final LockoutPolicy POLICY = new LockoutPolicy(3, Duration.ofMinutes(15));

    private User newUser() {
        return new User("locktest", "locktest@example.com", "irrelevant-hash");
    }

    @Test
    void recordFailedAttemptIncrementsCounterWithoutLockingBelowThreshold() {
        User user = newUser();

        boolean justLocked = user.recordFailedAttempt(POLICY, FIXED_CLOCK);

        assertThat(justLocked).isFalse();
        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.isLocked(FIXED_NOW)).isFalse();
    }

    @Test
    void recordFailedAttemptLocksExactlyAtThreshold() {
        User user = newUser();

        user.recordFailedAttempt(POLICY, FIXED_CLOCK);
        user.recordFailedAttempt(POLICY, FIXED_CLOCK);
        boolean justLocked = user.recordFailedAttempt(POLICY, FIXED_CLOCK);

        assertThat(justLocked).isTrue();
        assertThat(user.getFailedLoginAttempts()).isEqualTo(3);
        assertThat(user.getLockedUntil()).isEqualTo(FIXED_NOW.plus(POLICY.lockoutDuration()));
    }

    @Test
    void isLockedIsTrueBeforeExpiryAndFalseAfter() {
        User user = newUser();
        user.recordFailedAttempt(POLICY, FIXED_CLOCK);
        user.recordFailedAttempt(POLICY, FIXED_CLOCK);
        user.recordFailedAttempt(POLICY, FIXED_CLOCK);

        Instant lockedUntil = user.getLockedUntil();

        assertThat(user.isLocked(lockedUntil.minusSeconds(1))).isTrue();
        assertThat(user.isLocked(lockedUntil)).isFalse();
        assertThat(user.isLocked(lockedUntil.plusSeconds(1))).isFalse();
    }

    @Test
    void recordSuccessClearsLockoutAndStampsLastLoginAt() {
        User user = newUser();
        user.recordFailedAttempt(POLICY, FIXED_CLOCK);
        user.recordFailedAttempt(POLICY, FIXED_CLOCK);
        user.recordFailedAttempt(POLICY, FIXED_CLOCK);
        assertThat(user.getLockedUntil()).isNotNull();

        user.recordSuccess(FIXED_CLOCK);

        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getLastLoginAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void clearLockoutResetsAttemptsAndLockWithoutTouchingLastLoginAt() {
        User user = newUser();
        user.recordFailedAttempt(POLICY, FIXED_CLOCK);
        user.recordFailedAttempt(POLICY, FIXED_CLOCK);
        user.recordFailedAttempt(POLICY, FIXED_CLOCK);
        assertThat(user.getLockedUntil()).isNotNull();

        user.clearLockout();

        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        // clearLockout is used outside the login flow (e.g. password
        // reset) - it must not fabricate a "logged in just now" signal.
        assertThat(user.getLastLoginAt()).isNull();
    }
}
