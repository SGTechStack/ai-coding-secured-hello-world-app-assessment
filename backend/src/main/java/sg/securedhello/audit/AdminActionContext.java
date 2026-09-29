package sg.securedhello.audit;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * The context of the admin mutation rows: the acting administrator in {@code user.id}, the account acted on in
 * {@code user.target.id}, and for a refusal the guard's reason.
 *
 * @param userId   the acting administrator
 * @param targetId the account acted on
 * @param reason   why the guard refused, for a refusal only
 */
public record AdminActionContext(UUID userId, UUID targetId, @Nullable AdminRefusalReason reason)
        implements AuditContext {

    /** An applied mutation (rows 28 and 29). */
    public static AdminActionContext applied(UUID userId, UUID targetId) {
        return new AdminActionContext(userId, targetId, null);
    }

    /** A refused one (row 34). */
    public static AdminActionContext refused(UUID userId, UUID targetId, AdminRefusalReason reason) {
        return new AdminActionContext(userId, targetId, reason);
    }

    @Override
    public void writeTo(AuditFields fields) {
        fields.put(AuditKey.USER_ID, userId).put(AuditKey.USER_TARGET_ID, targetId);
        if (reason != null) {
            fields.reason(reason);
        }
    }
}
