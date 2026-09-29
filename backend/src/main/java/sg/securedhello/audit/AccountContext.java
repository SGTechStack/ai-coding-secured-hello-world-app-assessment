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

    /** Login success (row 1) and logout (row 7). */
    public static AccountContext of(UUID userId) {
        return new AccountContext(userId, null);
    }

    /** Login failure (row 2); {@code userId} only when the account resolved (REJ-042). */
    public static AccountContext loginFailure(@Nullable UUID userId, LoginFailureReason reason) {
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
