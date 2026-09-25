package com.example.demo_app.audit;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Who performed an audited action, and from which client address: the {@code actor} and {@code ip}
 * of every request's audit line. The web layer builds it from the request, so services can audit
 * (and, for admins, compare the actor with a target) without depending on the servlet API.
 *
 * @param username the signed-in or submitted username, or {@code null} for {@link
 *     AuditLog#ANONYMOUS}
 * @param ip the client address the request came from
 */
public record Actor(String username, String ip) {

  /** {@code username} acting through {@code request}. */
  public static Actor of(String username, HttpServletRequest request) {
    return new Actor(username, request.getRemoteAddr());
  }

  /** Nobody signed in, acting through {@code request}. */
  public static Actor anonymous(HttpServletRequest request) {
    return of(null, request);
  }
}
