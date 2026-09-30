package com.example.hello.auth;

import java.util.Map;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

/** "Log out everywhere" for one user, used by password reset and admin disable/role/delete. */
@Service
public class SessionInvalidationService {

  private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

  public SessionInvalidationService(
      FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
    this.sessionRepository = sessionRepository;
  }

  /** Deletes every server-side session belonging to {@code username}; returns how many. */
  public int invalidateAllForUser(String username) {
    Map<String, ? extends Session> sessions = sessionRepository.findByPrincipalName(username);
    sessions.keySet().forEach(sessionRepository::deleteById);
    return sessions.size();
  }
}
