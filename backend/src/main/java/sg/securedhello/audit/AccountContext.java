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

    /** Login success (row 1), logout (row 7) and a completed password reset. */
    public static AccountContext of(UUID userId) {
        return new AccountContext(userId, null);
    }

    /** Login failure (row 2); {@code userId} only when the account resolved (REJ-042). */
    public static AccountContext loginFailure(@Nullable UUID userId, LoginFailureReason reason) {
        return new AccountContext(userId, reason);
    }

    /** Lockout engaged (row 3). */
    public static AccountContext lockout(UUID userId) {
        return new AccountContext(userId, LockoutReason.THRESHOLD_REACHED);
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
