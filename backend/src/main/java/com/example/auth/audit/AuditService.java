package com.example.auth.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Single choke point for all security-relevant Audit Events. All log lines
 * use logfmt key=value format for easy grepping. Passwords, hashes, and
 * reset tokens are never passed here — this class cannot log them.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    public void registrationSuccess(String username) {
        log.info("event=registration_success username={}", username);
    }

    public void loginSuccess(String username, String ip) {
        log.info("event=login_success username={} ip={}", username, ip);
    }

    public void loginFailure(String username, String ip) {
        log.info("event=login_failure username={} ip={}", username, ip);
    }

    public void accountLocked(String username) {
        log.info("event=account_locked username={}", username);
    }

    public void ipThrottled(String ip) {
        log.info("event=ip_throttled ip={}", ip);
    }

    public void passwordResetRequested(String username) {
        log.info("event=password_reset_requested username={}", username);
    }

    public void passwordResetCompleted(String username) {
        log.info("event=password_reset_completed username={}", username);
    }

    public void adminUserAction(String actor, String target, String action) {
        log.info("event=admin_user_action actor={} target={} action={}", actor, target, action);
    }
}
