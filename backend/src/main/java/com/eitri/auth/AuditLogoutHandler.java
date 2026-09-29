package com.eitri.auth;

import com.eitri.audit.AuditLogger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

/** Records a safe logout audit event while the session is still available for HMAC correlation. */
@Component
public final class AuditLogoutHandler implements LogoutHandler {

    private final AuditLogger auditLogger;

    public AuditLogoutHandler(AuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AccountPrincipal principal) {
            auditLogger.logoutSucceeded(principal.auditAccount(), request);
        }
    }
}
