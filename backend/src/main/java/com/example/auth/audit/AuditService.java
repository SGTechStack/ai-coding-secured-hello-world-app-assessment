package com.example.auth.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Single choke point for all security-relevant Audit Events. All log lines
 * use logfmt key=value format for easy grepping. Passwords, hashes, and
 * reset tokens are never passed here — this class cannot log them.
 *
 * User-supplied fields (username, ip) are sanitised via {@link #clean} to strip
 * control characters — chiefly CR/LF — so an attacker cannot forge or split log
 * lines by embedding newlines in a username (log injection, CWE-117).
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final int MAX_FIELD_LENGTH = 200;

    public void registrationSuccess(String username) {
        log.info("event=registration_success username={}", clean(username));
    }

    public void loginSuccess(String username, String ip) {
        log.info("event=login_success username={} ip={}", clean(username), clean(ip));
    }

    public void loginFailure(String username, String ip) {
        log.info("event=login_failure username={} ip={}", clean(username), clean(ip));
    }

    public void accountLocked(String username) {
        log.info("event=account_locked username={}", clean(username));
    }

    public void ipThrottled(String ip) {
        log.info("event=ip_throttled ip={}", clean(ip));
    }

    public void passwordResetRequested(String username) {
        log.info("event=password_reset_requested username={}", clean(username));
    }

    public void passwordResetCompleted(String username) {
        log.info("event=password_reset_completed username={}", clean(username));
    }

    public void adminUserAction(String actor, String target, String action) {
        log.info("event=admin_user_action actor={} target={} action={}",
                clean(actor), clean(target), clean(action));
    }

    /**
     * Removes control characters (including CR/LF) so a user-supplied value cannot
     * inject or split audit log lines, and caps length to bound log-line size.
     */
    private static String clean(String value) {
        if (value == null) {
            return "null";
        }
        String sanitized = value.replaceAll("\\p{Cntrl}", "_");
        return sanitized.length() > MAX_FIELD_LENGTH
                ? sanitized.substring(0, MAX_FIELD_LENGTH) + "..."
                : sanitized;
    }
}
