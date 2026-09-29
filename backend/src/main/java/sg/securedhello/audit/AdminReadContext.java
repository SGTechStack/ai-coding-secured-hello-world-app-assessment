package sg.securedhello.audit;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * The context of the admin read rows: the acting administrator in {@code user.id}, and either the account read, in
 * {@code user.target.id}, or the number of accounts listed, in {@code user.target.count}.
 *
 * @param userId   the acting administrator
 * @param targetId the account read, for a single read
 * @param count    the accounts returned, for a list
 */
public record AdminReadContext(UUID userId, @Nullable UUID targetId, @Nullable Integer count) implements AuditContext {

    /** The user-list row. */
    public static AdminReadContext listed(UUID userId, int count) {
        return new AdminReadContext(userId, null, count);
    }

    /** The single-user read row. */
    public static AdminReadContext viewed(UUID userId, UUID targetId) {
        return new AdminReadContext(userId, targetId, null);
    }

    @Override
    public void writeTo(AuditFields fields) {
        fields.put(AuditKey.USER_ID, userId);
        if (targetId != null) {
            fields.put(AuditKey.USER_TARGET_ID, targetId);
        }
        if (count != null) {
            fields.put(AuditKey.USER_TARGET_COUNT, count);
        }
    }
}
