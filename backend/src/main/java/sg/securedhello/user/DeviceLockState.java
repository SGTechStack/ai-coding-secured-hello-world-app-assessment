package sg.securedhello.user;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * A trusted device's lane of the password lockout, read and written as one value (ADR-075): the same windowed counter
 * and lock as the account's untrusted lane (ADR-011; ADR-012), kept per device. The NIST cap is not here: it stays on
 * the account and counts every lane's failures (ADR-013).
 *
 * @param failedLoginAttempts the windowed counter: consecutive failures, each less than the observation window after
 *                            the one before
 * @param lastFailedAt        when the device's last failure happened, the window's staleness anchor
 * @param lockedUntil         the device lock's end; the device is locked while it is in the future (REJ-017)
 * @param consecutiveFailures the device's failures since its last success, which picks its rung
 */
public record DeviceLockState(int failedLoginAttempts, @Nullable Instant lastFailedAt, @Nullable Instant lockedUntil,
        int consecutiveFailures) {

    /** No failures and no lock: a new device, a trusted success, or an admin unlock. */
    public static final DeviceLockState CLEAR = new DeviceLockState(0, null, null, 0);

    /** Whether the device is locked at {@code now}: {@code locked_until} is still ahead. */
    public boolean lockedAt(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }
}
