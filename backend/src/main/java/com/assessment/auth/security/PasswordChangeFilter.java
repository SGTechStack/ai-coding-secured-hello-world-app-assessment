package com.assessment.auth.security;

import com.assessment.auth.audit.AuditAction;
import com.assessment.auth.audit.AuditEvent;
import com.assessment.auth.audit.AuditLogger;
import com.assessment.auth.audit.AuditReason;
import com.assessment.auth.common.ApiErrorCode;
import com.assessment.auth.common.AuthenticatedUser;
import com.assessment.auth.common.ProblemDetailWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.slf4j.event.Level;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Tier-0 forced password change (spec.md S5, S4 filter 8).
 *
 * <p><strong>Preempts the authorization matrix.</strong> A flagged user is blocked from every
 * endpoint regardless of role — including a USER_MANAGER, who would otherwise be able to administer
 * the system while still holding a credential they were told to replace.
 *
 * <p>Exactly four paths are allowed through. The recipe names three; <strong>logout is our
 * addition</strong>, because without it a flagged user cannot end their own session.
 *
 * <p>It writes its own response body through the shared {@link ProblemDetailWriter} and never calls
 * {@code response.sendError} — banned chain-wide, and unable to carry the {@code code} that the
 * SPA's interceptor needs to tell this apart from a session death (spec.md S10).
 */
public class PasswordChangeFilter extends OncePerRequestFilter {

  private final Set<String> allowed;
  private final ProblemDetailWriter problemDetailWriter;
  private final AuditLogger auditLogger;

  public PasswordChangeFilter(
      String basePath, ProblemDetailWriter problemDetailWriter, AuditLogger auditLogger) {
    this.allowed =
        Set.of(
            "GET " + basePath + "/csrf",
            "GET " + basePath + "/currentUser",
            "PATCH " + basePath + "/currentUser/changePassword",
            "POST " + basePath + "/auth/logout");
    this.problemDetailWriter = problemDetailWriter;
    this.auditLogger = auditLogger;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || !(authentication.getPrincipal() instanceof AuthenticatedUser user)
        || !user.requirePasswordChange()) {
      chain.doFilter(request, response);
      return;
    }

    if (allowed.contains(request.getMethod() + " " + request.getRequestURI())) {
      chain.doFilter(request, response);
      return;
    }

    // The seventh source.ip class. Ticket 12 added this one as an amendment to ticket 18's six.
    auditLogger.emit(
        AuditEvent.of(
                AuditAction.AUTHORIZATION, AuditReason.FORCED_PASSWORD_CHANGE_DENIED, Level.WARN)
            .outcome("failure")
            .actor(user.id())
            .sourceIp(request.getRemoteAddr())
            .error("403", "authorization", "Change the account password, then retry.")
            .build());
    problemDetailWriter.write(
        response,
        ApiErrorCode.PASSWORD_CHANGE_REQUIRED,
        "A password change is required before this account can be used.");
  }
}
