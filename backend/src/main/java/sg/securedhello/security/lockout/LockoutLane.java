package sg.securedhello.security.lockout;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import sg.securedhello.security.source.DeviceClaim;
import sg.securedhello.security.source.SourceKeyAuthenticationDetails;

/**
 * The lane of the password lockout a sign-in counts in (ADR-075). A sign-in whose device cookie verified, names a
 * device issued to the account the submitted username resolves to, and is still within its lifetime, is <b>trusted</b>
 * and counts in that device's own lane. Anything else is <b>untrusted</b> and counts in the account's lane: no cookie,
 * a tampered one (its claim never exists), a revoked one (its device row is gone), an expired one, or one issued to
 * another account. Pure: no state, no lookups.
 *
 * @param device the trusted device, or {@code null} for the untrusted lane
 */
public record LockoutLane(@Nullable UUID device) {

    /** The account's untrusted lane. */
    public static final LockoutLane UNTRUSTED = new LockoutLane(null);

    /** The lane {@code details} put a sign-in to account {@code accountId} in, at {@code now}. */
    public static LockoutLane of(@Nullable Object details, UUID accountId, Instant now) {
        DeviceClaim claim = claimOf(details);
        return claim != null && trusts(claim, accountId, now) ? new LockoutLane(claim.deviceId()) : UNTRUSTED;
    }

    /**
     * Whether a sign-in to account {@code accountId} is refused by its lane's lock at {@code now}: a trusted device's
     * own lock, or else the account's untrusted lock, {@code untrustedLocked}. An untrusted lock never refuses a
     * trusted device.
     */
    public static boolean locked(@Nullable Object details, UUID accountId, boolean untrustedLocked, Instant now) {
        DeviceClaim claim = claimOf(details);
        return claim != null && trusts(claim, accountId, now) ? claim.lockedAt(now) : untrustedLocked;
    }

    /** Whether this is a trusted device's lane. */
    public boolean trusted() {
        return device != null;
    }

    /** Whether {@code claim} makes a sign-in to {@code accountId} trusted at {@code now}. */
    private static boolean trusts(DeviceClaim claim, UUID accountId, Instant now) {
        return claim.userId().equals(accountId) && claim.expiresAt().isAfter(now);
    }

    private static @Nullable DeviceClaim claimOf(@Nullable Object details) {
        return details instanceof SourceKeyAuthenticationDetails source ? source.device() : null;
    }
}
