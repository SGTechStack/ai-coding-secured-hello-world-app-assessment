package com.example.helloauth.audit;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

/**
 * Turns Spring Security's authentication events into audit lines (ticket 14).
 * The {@code DefaultAuthenticationEventPublisher} wired onto the login
 * {@code ProviderManager} publishes these — credential verification is the
 * only place they can originate, so every event is a login outcome.
 *
 * <p>Service-level rejections are deliberately <em>not</em> covered here:
 * the IP-throttle and account-lock gates in
 * {@link com.example.helloauth.auth.LoginService} throw before
 * {@code authenticate()} runs, so no event is published —
 * {@link AuditLogger#loginThrottled} and {@code loginFailed(…, "locked")}
 * are emitted directly by the service instead.
 */
@Component
public class AuditAuthenticationEvents {

    private final AuditLogger audit;

    public AuditAuthenticationEvents(AuditLogger audit) {
        this.audit = audit;
    }

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        audit.loginSucceeded(event.getAuthentication().getName());
    }

    @EventListener
    public void onAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
        audit.loginFailed(
            event.getAuthentication().getName(), reasonOf(event.getException()));
    }

    /**
     * Stable snake_case reasons — never the exception's free-form message
     * and never its class name (an implementation detail that leaks into a
     * vocabulary downstream parsers switch on). Unmapped exceptions collapse
     * to {@link AuditLogger.Reasons#UNKNOWN}.
     */
    private static String reasonOf(AuthenticationException exception) {
        if (exception instanceof BadCredentialsException) {
            return AuditLogger.Reasons.BAD_CREDENTIALS;
        }
        if (exception instanceof DisabledException) {
            return AuditLogger.Reasons.DISABLED;
        }
        if (exception instanceof LockedException) {
            return AuditLogger.Reasons.LOCKED;
        }
        return AuditLogger.Reasons.UNKNOWN;
    }
}
