package com.example.auth.session;

import java.util.Set;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

/**
 * Terminates all server-side sessions belonging to a given principal, using
 * Spring Session JDBC's principal-name index (ADR-0001).
 *
 * Used to enforce "log out everywhere" on password reset (Story 7) and — from
 * Slice 6 — on admin disable / role-change (Stories 43–44). Because Spring
 * Session persists sessions in the shared datastore, deleting them here means
 * any stale session cookie is rejected on its next request.
 */
@Service
public class SessionInvalidator {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    public SessionInvalidator(FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    /** Deletes every active session for the given username. No-op if none exist. */
    public void invalidateAllSessions(String username) {
        Set<String> sessionIds = sessionRepository
                .findByPrincipalName(username)
                .keySet();
        for (String sessionId : sessionIds) {
            sessionRepository.deleteById(sessionId);
        }
    }
}
