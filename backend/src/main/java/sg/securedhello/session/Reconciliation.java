package sg.securedhello.session;

import java.util.Map;
import java.util.stream.Stream;

import sg.securedhello.audit.SessionReconciliationContext;

/**
 * What one reconciliation sweep ended (ADR-039).
 *
 * @param sessionsEnded the sessions ended
 * @param accounts      the accounts reconciled under each trigger; a trigger with none may be absent
 */
public record Reconciliation(int sessionsEnded, Map<ReconciliationTrigger, Integer> accounts) {

    public Reconciliation {
        accounts = Map.copyOf(accounts);
    }

    /** The accounts reconciled under {@code trigger}. */
    public int accounts(ReconciliationTrigger trigger) {
        return accounts.getOrDefault(trigger, 0);
    }

    /** The audit row's context: every trigger in precedence order, zeros included. */
    SessionReconciliationContext auditContext() {
        return new SessionReconciliationContext(sessionsEnded, Stream.of(ReconciliationTrigger.values())
                .map(trigger -> trigger + "=" + accounts(trigger)).toList());
    }
}
