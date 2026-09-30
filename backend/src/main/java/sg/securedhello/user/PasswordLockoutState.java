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

    /** No failures, no lock and no cap: what a rebinding, a reset redemption, leaves (ADR-009). */
    public static final PasswordLockoutState CLEAR = new PasswordLockoutState(0, null, null, 0, null);

    /** Whether the account is locked at {@code now}: {@code locked_until} is still ahead. */
    public boolean lockedAt(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /**
     * What an admin unlock leaves (REJ-072): the windowed counter, its anchor and the lock cleared. The cap counter and
     * a cap's disable stay, since only a rebinding clears them (ADR-013).
     */
    public PasswordLockoutState unlocked() {
        return new PasswordLockoutState(0, null, null, consecutiveFailuresSinceSuccess, passwordDisabledAt);
    }

    /** Whether the NIST cap has disabled the password authenticator. */
    public boolean passwordDisabled() {
        return passwordDisabledAt != null;
    }
}
