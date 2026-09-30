package com.assessment.auth.security;

import com.assessment.auth.audit.AuditAction;
import com.assessment.auth.audit.AuditEvent;
import com.assessment.auth.audit.AuditLogger;
import com.assessment.auth.audit.AuditReason;
import com.assessment.auth.common.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.event.Level;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Logout (story 1.6).
 *
 * <p>A controller rather than Spring Security's {@code LogoutFilter}, because matrix row 19 governs
 * this endpoint like every other and the matrix is the sole authorization mechanism — a filter
 * would sit outside it and quietly become a second one.
 *
 * <p><strong>CSRF protection is retained deliberately.</strong> That means logout on an
 * already-expired session answers 401, and Std:438 forbids "fixing" that: the SPA absorbs it
 * rather than showing an error, because a 401 on logout means the session is already gone, which
 * is the outcome the caller wanted.
 *
 * <p>{@code Clear-Site-Data} is what makes logout mean something on the client as well as the
 * server.
 */
@RestController
public class LogoutController {

  private final AuditLogger auditLogger;

  public LogoutController(AuditLogger auditLogger) {
    this.auditLogger = auditLogger;
  }

  @PostMapping("${api.base-path}/auth/logout")
  @ResponseStatus(HttpStatus.OK)
  public void logout(
      @AuthenticationPrincipal AuthenticatedUser user,
      HttpServletRequest request,
      HttpServletResponse response) {

    if (user != null) {
      auditLogger.emit(
          AuditEvent.of(AuditAction.SESSION_MANAGEMENT, AuditReason.LOGOUT, Level.INFO)
              .actor(user.id())
              .build());
    }

    HttpSession session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }
    SecurityContextHolder.clearContext();
    response.setHeader("Clear-Site-Data", "\"cache\",\"cookies\",\"storage\"");
  }
}
