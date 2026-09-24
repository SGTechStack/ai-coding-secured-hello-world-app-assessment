package com.example.helloauth.session;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

/**
 * The one canonical home for per-user session invalidation — the ticket-02
 * mechanism ({@code findByPrincipalName} + {@code deleteById}) on the
 * indexed session repository.
 *
 * <p>This is a fail-closed security helper: it runs wherever a live session
 * would otherwise out-privilege the account it belongs to — password reset
 * (a stolen session dies with the old password), admin disable/delete, and
 * ADMIN&rarr;USER demotion (a stored {@code SecurityContext} keeps its
 * original authorities for the session's lifetime). Keeping a single
 * implementation means every caller invalidates exactly the same way.
 *
 * <p>Callers run it inside their own {@code @Transactional} boundary so the
 * Spring Session JDBC deletes join the same datasource transaction as the
 * account change.
 */
@Service
public class SessionInvalidationService {

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public SessionInvalidationService(
            FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    /** Deletes every session the named principal holds. */
    public void invalidateAllFor(String username) {
        sessions.findByPrincipalName(username)
            .keySet()
            .forEach(sessions::deleteById);
    }
}
