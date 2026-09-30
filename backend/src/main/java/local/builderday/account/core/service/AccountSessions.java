package local.builderday.account.core.service;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/** Ends an account's Sessions in the shared Spring Session store, whichever instance created them. */
@Component
public class AccountSessions {
  private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

  AccountSessions(FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
    this.sessionRepository = sessionRepository;
  }

  /**
   * Ends every Session of the account (OWASP: terminate sessions when account state or credentials change).
   *
   * @param username the normalized username, which is the Session's principal name
   */
  public void endAll(String username) {
    sessionRepository.findByPrincipalName(username).keySet().forEach(sessionRepository::deleteById);
  }
}
