package sg.securedhello.session;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The one seam that ends an account's sessions (ADR-037). Every trigger calls it: credential change and reset,
 * admin disable, role change, deletion, lockout and the failure cap.
 *
 * <p><b>After commit, never inside the transaction</b> (ADR-039). Called inside a transaction, the kill is registered
 * to run once it commits, outside every row lock, and a rollback cancels it. Spring Session runs its own writes in
 * {@code REQUIRES_NEW}, so there is nothing to gain by running it earlier. Called outside a transaction, it runs at
 * once. A kill that fails after commit is logged and never undoes the committed change; the startup reconciliation
 * sweep is the repair for triggers that leave durable state.
 *
 * <p>Sessions are found through the {@code PRINCIPAL_NAME} index, which holds the username the session signed in
 * with (T-SES-009).
 */
@Service
public class SessionTerminationService {

    private static final Logger log = LoggerFactory.getLogger(SessionTerminationService.class);

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public SessionTerminationService(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    /** Ends every session of {@code username}: reset redemption and the admin and lockout triggers. */
    public void endAll(String username) {
        dispatch(username, null);
    }

    /**
     * Ends every session of {@code username} except {@code keptSessionId}: self-service change and forced-change
     * completion, where the acting session survives (ADR-035). The caller rotates the kept session's id afterwards.
     */
    public void endAllExcept(String username, String keptSessionId) {
        dispatch(username, keptSessionId);
    }

    private void dispatch(String username, @Nullable String keptSessionId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            end(username, keptSessionId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                end(username, keptSessionId);
            }
        });
    }

    private void end(String username, @Nullable String keptSessionId) {
        try {
            sessions.findByPrincipalName(username).keySet().stream()
                    .filter(id -> !id.equals(keptSessionId))
                    .forEach(sessions::deleteById);
        } catch (RuntimeException ex) {
            log.error("Ending an account's sessions after commit failed; the committed change stands", ex);
        }
    }
}
