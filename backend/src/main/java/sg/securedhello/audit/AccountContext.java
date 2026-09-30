package sg.securedhello.audit;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * The context of the account rows: the account's UUID, when there is one, and the row's reason, when it has one.
 * Build it with the factory for the row, which types the reason by its family (ADR-055).
 *
 * @param userId {@code user.id}, or {@code null} when no account resolved
 * @param reason {@code event.reason}, or {@code null} for a row without a reason
 */
public record AccountContext(@Nullable UUID userId, @Nullable AuditReason reason) implements AuditContext {

    /** Login success (row 1), logout (row 7), a completed password reset, and the TOTP rows (36 to 39, 42). */
    public static AccountContext of(UUID userId) {
        return new AccountContext(userId, null);
    }

    /** Login failure (row 2); {@code userId} only when the account resolved (REJ-042). */
    public static AccountContext loginFailure(@Nullable UUID userId, LoginFailureReason reason) {
        return new AccountContext(userId, reason);
    }

    /** Lockout engaged (row 3), in the lane {@code reason} names (ADR-075). */
    public static AccountContext lockout(UUID userId, LockoutReason reason) {
        return new AccountContext(userId, reason);
    }

    /** Lockout cleared (row 4). */
    public static AccountContext lockCleared(UUID userId, LockoutClearReason reason) {
        return new AccountContext(userId, reason);
    }

    /** The password-failure alert (row 45). */
    public static AccountContext passwordFailureAlert(UUID userId) {
        return new AccountContext(userId, null);
    }

    /** The password authenticator was disabled. */
    public static AccountContext passwordDisabled(UUID userId, PasswordDisableReason reason) {
        return new AccountContext(userId, reason);
    }

    /** A lapsed pending registration deleted (row 48); {@code userId} is the deleted account. */
    public static AccountContext registrationLapsed(UUID userId) {
        return new AccountContext(userId, null);
    }

    /** Session start (row 8). */
    public static AccountContext sessionStart(UUID userId, SessionStartReason reason) {
        return new AccountContext(userId, reason);
    }

    /** CSRF refusal (row 13); {@code userId} only when the caller is signed in. */
    public static AccountContext csrfRejected(@Nullable UUID userId, CsrfReason reason) {
        return new AccountContext(userId, reason);
    }

    /** A password-reset request, which never names an account: the row must not confirm one exists (REJ-002). */
    public static AccountContext resetRequested() {
        return new AccountContext(null, null);
    }

    /** A source-axis throttle (row 5), which carries no identity. */
    public static AccountContext sourceThrottled(SourceThrottleReason reason) {
        return new AccountContext(null, reason);
    }

    /** A submitted-value throttle (row 6), which carries no identity by construction: the value may name no one. */
    public static AccountContext identifierThrottled() {
        return new AccountContext(null, IdentifierThrottleReason.RATE_LIMITED_IDENTIFIER);
    }

    /** A signed-in session ended at its absolute lifetime (row 9). */
    public static AccountContext sessionExpired(UUID userId) {
        return new AccountContext(userId, SessionTimeoutReason.ABSOLUTE_TIMEOUT);
    }

    /** A session displaced by a newer sign-in of its account (row 10); {@code userId} is the displaced account. */
    public static AccountContext sessionEvicted(UUID userId) {
        return new AccountContext(userId, SessionEvictionReason.CONCURRENT_EVICTION);
    }

    /** A session cookie not honoured as presented (row 11), which carries no identity: no session resolved. */
    public static AccountContext invalidSession(InvalidSessionReason reason) {
        return new AccountContext(null, reason);
    }

    /** A signed-in caller refused (row 12). */
    public static AccountContext accessDenied(UUID userId) {
        return new AccountContext(userId, AccessDeniedReason.INSUFFICIENT_ROLE);
    }

    /** The admin surface asked a signed-in administrator for the second factor (row 14). */
    public static AccountContext factorRequired(UUID userId, FactorRequiredReason reason) {
        return new AccountContext(userId, reason);
    }

    /** The self-read (row 35). */
    public static AccountContext profileRead(UUID userId) {
        return new AccountContext(userId, null);
    }

    /** A registration that created a new pending registration (row 16), named by its new {@code userId}. */
    public static AccountContext registrationCreated(UUID userId) {
        return new AccountContext(userId, RegistrationReason.NEW_ACCOUNT);
    }

    /**
     * A registration whose address was already known (row 16). It never names the account: the registrant is
     * anonymous, and the address's state must not become readable from the row's shape.
     */
    public static AccountContext registrationOfExistingAddress() {
        return new AccountContext(null, RegistrationReason.EXISTING_ADDRESS);
    }

    /** A registration refused for its username (row 17), which names no account. */
    public static AccountContext registrationRefused() {
        return new AccountContext(null, RegistrationRefusalReason.USERNAME_UNAVAILABLE);
    }

    /** A token that did not redeem (row 20), which names no account: the submitter is anonymous. */
    public static AccountContext tokenRefused(TokenRedemptionFailureReason reason) {
        return new AccountContext(null, reason);
    }

    @Override
    public void writeTo(AuditFields fields) {
        if (userId != null) {
            fields.put(AuditKey.USER_ID, userId);
        }
        if (reason != null) {
            fields.reason(reason);
        }
    }
}
