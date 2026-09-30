package sg.securedhello.security.source;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * What a sign-in's device cookie claims, once its signature has verified and its device row was found (ADR-075): the
 * device, the account it was issued to, when it stops being trusted, and the device lane's lock when it was read. It
 * is read from the cookie alone, before anything looks the submitted username up, so its lookup costs the same whether
 * or not that account exists. Whether it is trusted for this sign-in is decided against the account later
 * ({@code LockoutLane}); a claim for another account is untrusted.
 *
 * <p>It travels in {@link SourceKeyAuthenticationDetails}, which the session stores, so it holds no secret: never the
 * cookie value or its HMAC.
 *
 * @param deviceId    the trusted device the cookie names
 * @param userId      the account the device was issued to
 * @param expiresAt   when the device stops being trusted
 * @param lockedUntil the device lane's lock end when the claim was read, or {@code null} if none was recorded
 */
public record DeviceClaim(UUID deviceId, UUID userId, Instant expiresAt, @Nullable Instant lockedUntil)
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Whether the device lane was locked at {@code now}, as far as the claim's read shows. */
    public boolean lockedAt(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }
}
