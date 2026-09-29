package com.eitri.auth;

import com.eitri.audit.AuditAccount;
import com.eitri.audit.AuditLogger;
import com.eitri.config.SessionSecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Rejects sessions whose total age has reached the configured absolute lifetime. */
public final class AbsoluteSessionTimeoutFilter extends OncePerRequestFilter {

    private final Clock clock;
    private final SessionSecurityProperties properties;
    private final AuditLogger auditLogger;

    public AbsoluteSessionTimeoutFilter(
            Clock clock, SessionSecurityProperties properties, AuditLogger auditLogger) {
        this.clock = clock;
        this.properties = properties;
        this.auditLogger = auditLogger;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        HttpSession session = currentSession(request);
        if (session != null && hasReachedAbsoluteLifetime(session)) {
            auditLogger.absoluteSessionExpired(currentAccount(), session);
            invalidate(session);
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean hasReachedAbsoluteLifetime(HttpSession session) {
        try {
            Instant deadline = Instant.ofEpochMilli(session.getCreationTime())
                    .plus(properties.absoluteLifetime());
            return !clock.instant().isBefore(deadline);
        } catch (IllegalStateException alreadyInvalidated) {
            return false;
        }
    }

    private static HttpSession currentSession(HttpServletRequest request) {
        try {
            return request.getSession(false);
        } catch (IllegalStateException alreadyInvalidated) {
            return null;
        }
    }

    private static AuditAccount currentAccount() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AccountPrincipal principal
                ? principal.auditAccount()
                : null;
    }

    private static void invalidate(HttpSession session) {
        try {
            session.invalidate();
        } catch (IllegalStateException alreadyInvalidated) {
            // A concurrent request already ended it; the current request is still rejected.
        }
    }
}
