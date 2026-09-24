package com.example.demo_app.security;

import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * Signs a user out everywhere: the one "expire all sessions for user X" operation that a password
 * reset, and the admin's disable, role change and delete, all call.
 *
 * <p>It marks each of the user's sessions in the {@link SessionRegistry} as expired. The session
 * itself ends on its next request, which {@code ConcurrentSessionFilter} answers with the JSON
 * {@code 401} ({@link JsonSecurityErrorHandler}) after invalidating it and expiring the cookie. The
 * registry is in memory, so this covers the sessions of this instance only, as the spec intends.
 */
@Component
public class SessionExpiry {

  private final SessionRegistry sessionRegistry;

  SessionExpiry(SessionRegistry sessionRegistry) {
    this.sessionRegistry = sessionRegistry;
  }

  /**
   * Expires every live session of the account named {@code username} (the stored, lowercase
   * name). A user with no session is a no-op.
   */
  public void expireAllSessionsOf(String username) {
    sessionRegistry.getAllPrincipals().stream()
        .filter(
            principal ->
                principal instanceof UserDetails user && user.getUsername().equals(username))
        .flatMap(principal -> sessionRegistry.getAllSessions(principal, false).stream())
        .forEach(session -> session.expireNow());
  }
}
