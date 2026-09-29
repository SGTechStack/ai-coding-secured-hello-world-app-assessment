package sg.securedhello.user;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * An account's password-lockout columns, read and written as one value. Two counters with different jobs: the windowed
 * counter locks the account for a while (ADR-011; ADR-012), and the cap counter, which only a successful password
 * resets, disables the password authenticator at the NIST cap (ADR-013).
 *
 * @param failedLoginAttempts             the windowed counter: consecutive failures, each less than the observation
 *                                        window after the one before
 * @param lastFailedAt                    when the last failure happened, the window's staleness anchor
 * @param lockedUntil                     the lock's end; the account is locked while it is in the future (REJ-017)
 * @param consecutiveFailuresSinceSuccess the cap counter: every failure since the last successful password
 * @param passwordDisabledAt              when the cap disabled the password, until it is rebound
 */
public record PasswordLockoutState(int failedLoginAttempts, @Nullable Instant lastFailedAt,
        @Nullable Instant lockedUntil, int consecutiveFailuresSinceSuccess, @Nullable Instant passwordDisabledAt) {

    /** Whether the account is locked at {@code now}: {@code locked_until} is still ahead. */
    public boolean lockedAt(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Whether the NIST cap has disabled the password authenticator. */
    public boolean passwordDisabled() {
        return passwordDisabledAt != null;
    }
}
