package sg.securedhello.audit;

import java.util.List;

/**
 * The reconciliation row's context: what the startup sweep ended (ADR-039). Counts only; no account is named.
 *
 * @param sessionsEnded      the sessions ended
 * @param reconciledAccounts {@code <trigger>=<accounts>} for every trigger, zeros included
 */
public record SessionReconciliationContext(long sessionsEnded, List<String> reconciledAccounts)
        implements AuditContext {

    @Override
    public void writeTo(AuditFields fields) {
        fields.put(AuditKey.SESSIONS_ENDED_COUNT, sessionsEnded)
                .put(AuditKey.RECONCILED_ACCOUNTS, reconciledAccounts);
    }
}
