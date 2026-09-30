package sg.securedhello.admin;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * Why an account can or cannot sign in right now, apart from being enabled (ADR-075): its password lockout in each lane,
 * the NIST cap's disable, and its factor's two tiers. States and times only: never a hash, secret, device id or cookie
 * value (T-ADM-008). Each lock is shown only while it is in force.
 *
 * @param accountLockedUntil       the end of the account's untrusted-lane lock, or {@code null} if it is not locked
 * @param failuresSinceSuccess     wrong passwords since the last correct one, in every lane: the NIST cap's count
 *                                 (ADR-013)
 * @param lockedDevices            how many of the account's trusted devices are locked in their own lane
 * @param nextDeviceUnlock         when the first of those device locks ends, or {@code null} if none is locked
 * @param capDisabledAt            when the NIST cap disabled the password, until it is rebound, or {@code null}
 * @param factorLockedUntil        the end of the TOTP factor's tier-1 lock, or {@code null} if it is not locked
 *                                 (ADR-027)
 * @param factorDisabled           whether TOTP tier 2 has disabled the factor, until an administrator resets it
 */
public record SignInStatus(@Nullable Instant accountLockedUntil, int failuresSinceSuccess, int lockedDevices,
        @Nullable Instant nextDeviceUnlock, @Nullable Instant capDisabledAt, @Nullable Instant factorLockedUntil,
        boolean factorDisabled) {
}
