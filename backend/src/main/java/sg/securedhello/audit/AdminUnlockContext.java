package sg.securedhello.audit;

import java.util.UUID;

/**
 * The context of the unlock row (row 32): the acting administrator in {@code user.id}, the account unlocked in
 * {@code user.target.id} and the reason given in {@code user.target.unlock_reason} (REJ-028).
 *
 * @param userId   the acting administrator
 * @param targetId the account unlocked
 * @param reason   why it was unlocked
 */
public record AdminUnlockContext(UUID userId, UUID targetId, UnlockReason reason) implements AuditContext {

    @Override
    public void writeTo(AuditFields fields) {
        fields.put(AuditKey.USER_ID, userId).put(AuditKey.USER_TARGET_ID, targetId)
                .put(AuditKey.USER_TARGET_UNLOCK_REASON, reason);
    }
}
