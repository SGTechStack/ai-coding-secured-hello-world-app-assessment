package org.eds.demo.user.application;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

/**
 * Ends the server-side sessions of an Account, found through the principal index of the JDBC
 * session store (ADR-DEMO-BE-0001). Reused wherever a security change must cut off existing
 * sessions: password change, admin reset, disable and delete.
 *
 * <p>Ended sessions are deleted, so a replayed cookie is unauthenticated. Callers own the audit
 * line; this service logs nothing.
 */
@Service
@RequiredArgsConstructor
public class AccountSessionTerminator {

  private final FindByIndexNameSessionRepository<? extends Session> sessions;

  /** Ends every session of {@code username}. */
  public void endAllSessions(String username) {
    endSessions(username, Set.of());
  }

  /**
   * Ends every session of {@code username} except {@code currentSessionId}, so the holder who just
   * acted stays signed in.
   */
  public void endAllSessionsExcept(String username, String currentSessionId) {
    endSessions(username, Set.of(currentSessionId));
  }

  private void endSessions(String username, Set<String> keep) {
    sessions.findByPrincipalName(username).keySet().stream()
        .filter(id -> !keep.contains(id))
        .forEach(sessions::deleteById);
  }
}
