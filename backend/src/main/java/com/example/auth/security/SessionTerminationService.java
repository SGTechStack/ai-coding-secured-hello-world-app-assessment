package com.example.auth.security;

import com.example.auth.audit.AuditLogger;
import java.util.Set;
import java.util.UUID;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Deletes every Spring Session (JDBC) session belonging to a user -- used after a password reset
 * (PRD Story 7) and after an admin disables, re-roles or deletes an account, so the change takes
 * effect immediately rather than at the target's next login.
 *
 * <p>Sessions are looked up through Spring Session's principal-name index and deleted outright
 * (not just flagged), so they can't be resurrected. When called inside a transaction the deletion
 * is deferred until after commit: sweeping before commit would let a session created in the gap
 * survive, and a rolled-back change must not log users out.
 */
@Component
public class SessionTerminationService {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;
    private final AuditLogger auditLogger;

    public SessionTerminationService(
            FindByIndexNameSessionRepository<? extends Session> sessionRepository, AuditLogger auditLogger) {
        this.sessionRepository = sessionRepository;
        this.auditLogger = auditLogger;
    }

    /** {@code reason} is a fixed code such as {@code password_reset} or {@code account_disabled}. */
    public void terminateAllSessions(String username, UUID userId, String reason) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    terminateNow(username, userId, reason);
                }
            });
        } else {
            terminateNow(username, userId, reason);
        }
    }

    private void terminateNow(String username, UUID userId, String reason) {
        Set<String> sessionIds = sessionRepository.findByPrincipalName(username).keySet();
        sessionIds.forEach(sessionRepository::deleteById);
        auditLogger.sessionsTerminated(userId, reason, sessionIds.size());
    }
}
