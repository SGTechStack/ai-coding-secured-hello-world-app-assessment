package com.example.auth.audit;

import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Writes to a dedicated {@code AUDIT} logger (not the app's default,
 * class-named loggers) so audit events are distinguishable from ordinary
 * application logs by logger name -- there is no logback config in this
 * codebase routing {@code AUDIT} to a separate appender yet, so today it
 * still lands in the same sink as everything else. Plain {@code key=value}
 * lines -- no JSON encoder dependency -- since nothing here parses these
 * lines programmatically yet.
 *
 * <p>Every parameter is attacker-controlled free text (a login form
 * username, an admin-supplied role, etc.), so it is sanitized before being
 * interpolated: SLF4J's {@code {}} placeholder prevents format-string
 * injection but does not strip newlines, which would otherwise let a
 * crafted value forge extra {@code event=}/{@code username=}-shaped log
 * lines in an audit trail meant to be trustworthy evidence.
 *
 * <p>Called from inside {@code @Transactional} service methods (see {@code
 * AdminUserService}, {@code PasswordResetService}) -- safe only because
 * every method here is a synchronous, local, in-process log write with no
 * network I/O. If the {@code AUDIT} logger is ever routed through a network
 * appender, move these calls outside the transaction boundary, the same way
 * email-sending is kept out of it.
 */
@Component
public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger("AUDIT");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}]");
    private static final int MAX_FIELD_LENGTH = 100;

    public void loginSuccess(String username) {
        log.info("event=login_success username={}", sanitize(username));
    }

    public void loginFailure(String username) {
        log.info("event=login_failure username={}", sanitize(username));
    }

    public void accountLocked(String username) {
        log.info("event=account_locked username={}", sanitize(username));
    }

    public void passwordResetRequested(String username) {
        log.info("event=password_reset_requested username={}", sanitize(username));
    }

    public void passwordResetCompleted(String username) {
        log.info("event=password_reset_completed username={}", sanitize(username));
    }

    public void roleChanged(String actor, String targetUsername, String newRole) {
        log.info(
                "event=role_changed actor={} target={} newRole={}",
                sanitize(actor),
                sanitize(targetUsername),
                sanitize(newRole));
    }

    public void accountEnabled(String actor, String targetUsername) {
        log.info("event=account_enabled actor={} target={}", sanitize(actor), sanitize(targetUsername));
    }

    public void accountDisabled(String actor, String targetUsername) {
        log.info("event=account_disabled actor={} target={}", sanitize(actor), sanitize(targetUsername));
    }

    public void accountDeleted(String actor, String targetUsername) {
        log.info("event=account_deleted actor={} target={}", sanitize(actor), sanitize(targetUsername));
    }

    /**
     * Strips control characters (newlines included) so a crafted field value
     * can never inject a forged extra log line, and caps length so a huge
     * input can't be used to bloat the audit log cheaply.
     */
    private static String sanitize(String value) {
        if (value == null) {
            return "null";
        }
        String cleaned = CONTROL_CHARS.matcher(value).replaceAll("_");
        return cleaned.length() > MAX_FIELD_LENGTH ? cleaned.substring(0, MAX_FIELD_LENGTH) + "...(truncated)" : cleaned;
    }
}
