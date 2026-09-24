package com.example.helloauth.audit;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import net.logstash.logback.marker.Markers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The single emission point for structured audit events (ticket 06 decision,
 * ticket 14). Every call writes exactly one line to the dedicated
 * {@code audit} SLF4J logger, which {@code logback-spring.xml} routes to a
 * LogstashEncoder console appender — one machine-parseable JSON object per
 * event, with each map entry inlined as a top-level JSON field.
 *
 * <p>Field conventions: {@code event} names the action; {@code actor} is the
 * principal performing it, {@code target} the account it lands on (per the
 * PRD's actor+target requirement). Event-specific details ride as extra
 * fields ({@code reason}, {@code enabled}, {@code role},
 * {@code locked_until}, {@code remote_addr}, {@code email}).
 *
 * <p><b>Emission is commit-aware:</b> inside an active transaction the line
 * is deferred to {@code afterCommit}, so a mutation that rolls back (flush-
 * time constraint violation, tx failure) never leaves an audit line claiming
 * it happened — for an audit log, a false-positive line is the worst failure
 * mode. With no active transaction the event emits immediately (the
 * non-transactional call sites — throttled/locked login rejections — are
 * statements about the request, not about persisted state).
 *
 * <p><b>Hard rule:</b> no method here accepts a password, token, or secret —
 * credentials never appear in an audit line. (The stubbed
 * {@link com.example.helloauth.passwordreset.EmailService} is the sole
 * deliberate exception for the plaintext reset token, on a different logger.)
 */
@Component
public class AuditLogger {

    /** Dedicated logger name — logback-spring.xml binds it to the JSON appender. */
    public static final String LOGGER_NAME = "audit";

    private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);

    /**
     * The closed {@code reason} vocabulary for {@code login_failure} events —
     * one home so downstream parsers can switch on a stable set of
     * snake_case values. {@code bad_credentials}/{@code disabled}/
     * {@code locked} come from the authentication events
     * ({@link AuditAuthenticationEvents}); {@code ip_throttled} and
     * {@code locked} are also emitted directly by the service-level gates
     * that never reach the AuthenticationManager (no event fires for them).
     */
    public static final class Reasons {

        public static final String BAD_CREDENTIALS = "bad_credentials";
        public static final String DISABLED = "disabled";
        public static final String LOCKED = "locked";
        public static final String IP_THROTTLED = "ip_throttled";
        public static final String UNKNOWN = "unknown";

        private Reasons() {
        }
    }

    /** Successful authentication (via the wired AuthenticationEventPublisher). */
    public void loginSucceeded(String username) {
        emit("login_success", fields("actor", username));
    }

    /**
     * A rejected login attempt. {@code reason} pins the rejection layer —
     * one of {@link Reasons}; see its javadoc for which layer emits which.
     */
    public void loginFailed(String username, String reason) {
        emit("login_failure", fields("actor", username, "reason", reason));
    }

    /** The service-level IP-throttle rejection — a login failure with the source IP. */
    public void loginThrottled(String username, String remoteAddr) {
        emit("login_failure", fields("actor", username,
            "reason", Reasons.IP_THROTTLED, "remote_addr", remoteAddr));
    }

    /** The account lockout engaged — the Nth failure inside the window. */
    public void accountLocked(String username, Instant lockedUntil) {
        emit("account_locked", fields("target", username,
            "locked_until", lockedUntil.toString()));
    }

    /**
     * A reset link was requested. {@code email} is the caller-supplied
     * address — logged whether or not it matches an account (the request
     * itself is the auditable act; the token never appears here).
     */
    public void passwordResetRequested(String email) {
        emit("password_reset_requested", fields("email", email));
    }

    /** A reset token was consumed and the password changed. */
    public void passwordResetCompleted(String username) {
        emit("password_reset_completed", fields("target", username));
    }

    /** Admin enable/disable of another account. */
    public void adminStatusChanged(String actor, String target, boolean enabled) {
        emit("admin_status_changed", fields("actor", actor, "target", target,
            "enabled", enabled));
    }

    /** Admin USER/ADMIN role change on another account. */
    public void adminRoleChanged(String actor, String target, String role) {
        emit("admin_role_changed", fields("actor", actor, "target", target,
            "role", role));
    }

    /** Admin deletion of another account. */
    public void adminUserDeleted(String actor, String target) {
        emit("admin_user_deleted", fields("actor", actor, "target", target));
    }

    /**
     * Emits the event now, or once the surrounding transaction commits.
     * Deferred lines describe state that only exists after commit; a
     * roll-back leaves no trace on the audit log.
     */
    private void emit(String event, Map<String, Object> fields) {
        fields.put("event", event);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        write(fields);
                    }
                });
        } else {
            write(fields);
        }
    }

    /**
     * Writes one JSON line: the marker carries the fields (inlined by
     * LogstashEncoder), the message carries the event name so the line stays
     * readable in non-JSON renderings too.
     */
    private void write(Map<String, Object> fields) {
        log.info(Markers.appendEntries(fields),
            String.valueOf(fields.get("event")));
    }

    /** Pairwise key/value list → ordered field map (keys are field names). */
    private static Map<String, Object> fields(Object... keyValues) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            fields.put((String) keyValues[i], keyValues[i + 1]);
        }
        return fields;
    }
}
