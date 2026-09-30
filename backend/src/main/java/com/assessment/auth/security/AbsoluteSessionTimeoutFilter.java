package com.assessment.auth.security;

import com.assessment.auth.audit.AuditAction;
import com.assessment.auth.audit.AuditEvent;
import com.assessment.auth.audit.AuditLogger;
import com.assessment.auth.audit.AuditReason;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.event.Level;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Absolute session expiry (spec.md S4, filter 7).
 *
 * <p>Idle expiry (15 minutes) is the container's job via {@code server.servlet.session.timeout}.
 * <strong>Absolute expiry (8 hours) has no configuration key at all</strong>, which is the only
 * reason this filter exists.
 *
 * <p>It writes its own response rather than calling {@code response.sendError} (ArchUnit rule 5).
 * The body is <strong>empty</strong>, matching every other 401 in this application: {@code
 * HttpStatusEntryPoint} cannot write one, and an empty body is Std:247's most generic response.
 *
 * <p>Session age is measured against the injectable {@link Clock}, so the test advances time
 * instead of sleeping for eight hours (story 1.24).
 */
public class AbsoluteSessionTimeoutFilter extends OncePerRequestFilter {

  private final Duration absoluteTimeout;
  private final AuditLogger auditLogger;
  private final Clock clock;

  public AbsoluteSessionTimeoutFilter(
      Duration absoluteTimeout, AuditLogger auditLogger, Clock clock) {
    this.absoluteTimeout = absoluteTimeout;
    this.auditLogger = auditLogger;
    this.clock = clock;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    HttpSession session = request.getSession(false);
    if (session == null) {
      chain.doFilter(request, response);
      return;
    }

    Instant createdAt = Instant.ofEpochMilli(session.getCreationTime());
    if (createdAt.plus(absoluteTimeout).isAfter(clock.instant())) {
      chain.doFilter(request, response);
      return;
    }

    auditLogger.emit(
        AuditEvent.of(AuditAction.SESSION_MANAGEMENT, AuditReason.SESSION_EXPIRED, Level.INFO)
            .outcome("failure")
            .build());
    session.invalidate();
    SecurityContextHolder.clearContext();
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentLength(0);
    response.flushBuffer();
  }
}
