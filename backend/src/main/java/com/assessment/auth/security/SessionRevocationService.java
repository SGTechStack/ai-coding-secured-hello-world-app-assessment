package com.assessment.auth.security;

import java.util.Map;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

/**
 * Kills every server-side session belonging to one account (spec.md S4).
 *
 * <p><strong>Direct {@code deleteById}, not {@code expireNow()}.</strong> Three recipes prescribe
 * deletion against one parenthetical note suggesting expiry, and the mechanics settle it:
 * {@code expireNow()} only <em>marks</em> a session, while {@code deleteById} removes it. A marked
 * session is still a row an attacker's cookie can hit.
 *
 * <p>Called on five paths, all of them points where the account's authority or credential has
 * changed underneath an existing session: password reset confirm, self-service change, admin
 * disable, admin role change, admin delete.
 *
 * <p>The lookup is keyed on {@code PRINCIPAL_NAME}, which is why the {@code SPRING_SESSION_IX3}
 * index is load-bearing and asserted by its own test — {@code ddl-auto: validate} cannot catch its
 * absence, because these tables have no JPA entity (spec.md S2).
 */
@Service
public class SessionRevocationService {

  private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

  public SessionRevocationService(
      FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
    this.sessionRepository = sessionRepository;
  }

  /**
   * @param username the principal name Spring Session indexed the sessions under
   */
  public void revokeAllFor(String username) {
    Map<String, ? extends Session> sessions =
        sessionRepository.findByPrincipalName(username);
    sessions.keySet().forEach(sessionRepository::deleteById);
  }
}
