package com.example.helloauth.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Emits structured, single-line audit events. Never logs passwords or reset tokens (IM8 lm-19).
 * Format is a stable key=value schema suitable for ingestion (IM8 lm-15).
 */
@Component
public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger("AUDIT");

    public void loginSuccess(String username, String ip) {
        log.info("event=login_success username=\"{}\" ip=\"{}\"", safe(username), safe(ip));
    }

    public void loginFailure(String username, String ip, String reason) {
        log.warn("event=login_failure username=\"{}\" ip=\"{}\" reason=\"{}\"", safe(username), safe(ip), reason);
    }

    public void lockoutTriggered(String username, String ip) {
        log.warn("event=lockout_triggered username=\"{}\" ip=\"{}\"", safe(username), safe(ip));
    }

    public void ipThrottled(String ip) {
        log.warn("event=ip_throttled ip=\"{}\"", safe(ip));
    }

    public void passwordResetRequested(String email) {
        log.info("event=password_reset_requested email=\"{}\"", safe(email));
    }

    public void passwordResetCompleted(String username) {
        log.info("event=password_reset_completed username=\"{}\"", safe(username));
    }

    public void logout(String username) {
        log.info("event=logout username=\"{}\"", safe(username));
    }

    public void adminAction(String action, String actor, String target) {
        log.info("event=admin_action action={} actor=\"{}\" target=\"{}\"", action, safe(actor), safe(target));
    }

    public void adminSeeded(String username) {
        log.info("event=admin_seeded username=\"{}\"", safe(username));
    }

    public void forcedPasswordChange(String username) {
        log.info("event=forced_password_change username=\"{}\"", safe(username));
    }

    /** Neutralise CR/LF and quotes to prevent log injection. */
    private String safe(String v) {
        if (v == null) {
            return "";
        }
        return v.replaceAll("[\\r\\n\"]", "_");
    }
}
