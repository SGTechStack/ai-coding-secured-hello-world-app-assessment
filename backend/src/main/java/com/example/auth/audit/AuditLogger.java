package com.example.auth.audit;

import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.stereotype.Component;

/**
 * The single, typed entry point for security audit events (App-Standards logging recipe
 * "Centralising Audit Logging With A Typed Module"). Writes to a dedicated {@code AUDIT} logger
 * using the SLF4J fluent API, so each event becomes ECS key/value fields in the structured JSON
 * output ({@code logging.structured.format.console=ecs}) instead of text to be parsed.
 *
 * <p>Contract (see ADR-0011 and Log_Schema.md):
 * <ul>
 *   <li>Messages are static; everything dynamic is a key/value field.
 *   <li>Users are identified only by {@code user.id} (the actor) / {@code user.target.id} -- the
 *       per-user {@code public_id} UUID. Usernames, emails, passwords, tokens and raw session ids
 *       are never logged. Pre-authentication events omit {@code user.id} and rely on the
 *       {@code source.ip} / {@code session.hash} / {@code trace.id} that {@code
 *       RequestLoggingContextFilter} puts in MDC.
 *   <li>Successes log at INFO, failures and rejections at WARN.
 * </ul>
 *
 * <p>Every write is a synchronous, local log call, so it is safe to call from inside a
 * transaction. If the {@code AUDIT} logger is ever routed to a network appender, move calls out of
 * transactions the same way email sending is.
 */
@Component
public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger("AUDIT");
    private static final Pattern UNSAFE_CHARS = Pattern.compile("[\\p{Cntrl}\\u0085\\u2028\\u2029]");
    private static final int MAX_FIELD_LENGTH = 64;
    public static final String USER_ID = "user.id";

    // --- authentication -------------------------------------------------------------------

    public void loginSucceeded(UUID userId) {
        emit(event(Level.INFO, "User authenticated", "user-authentication", "authentication", "success"), userId);
    }

    /** {@code userId} is null when the username did not resolve to an account. */
    public void loginFailed(UUID userId, String reason) {
        emit(event(Level.WARN, "User authentication failed", "user-authentication", "authentication", "failure")
                        .addKeyValue("event.reason", sanitize(reason)),
                userId);
    }

    public void accountLocked(UUID userId) {
        emit(event(Level.WARN, "Account locked after repeated failures", "user-authentication", "authentication", "failure")
                        .addKeyValue("event.reason", "account_locked"),
                userId);
    }

    public void rateLimited(String limiter) {
        emit(event(Level.WARN, "Request rate limited", "ATTEMPTS_EXCEEDED", "authentication", "failure")
                        .addKeyValue("event.reason", "rate_limited")
                        .addKeyValue("labels.limiter", sanitize(limiter)),
                null);
    }

    public void loggedOut(UUID userId) {
        emit(event(Level.INFO, "User logged out", "user-logout", "session", "success"), userId);
    }

    /** {@code userId} is the user whose sessions ended; the actor (if any) comes from the request context. */
    public void sessionsTerminated(UUID userId, String reason, int count) {
        emit(event(Level.INFO, "User sessions terminated", "session-end", "session", "success")
                        .addKeyValue("user.target.id", str(userId))
                        .addKeyValue("event.reason", sanitize(reason))
                        .addKeyValue("labels.session_count", count),
                currentActor());
    }

    // --- authorisation --------------------------------------------------------------------

    /** {@code reason} is {@code forbidden} or {@code csrf}; {@code userId} is null for anonymous callers. */
    public void accessDenied(UUID userId, String reason) {
        emit(event(Level.WARN, "Access denied", "ACCESS_DENIED", "iam", "failure")
                        .addKeyValue("event.reason", sanitize(reason)),
                userId);
    }

    // --- account lifecycle ----------------------------------------------------------------

    public void registered(UUID userId) {
        emit(event(Level.INFO, "User registered", "user-provisioning", "iam", "success"), userId);
    }

    public void passwordResetRequested(UUID userId) {
        emit(event(Level.INFO, "Password reset requested", "password-reset", "iam", "success"), userId);
    }

    public void passwordResetCompleted(UUID userId) {
        emit(event(Level.INFO, "Password reset completed", "password-reset", "iam", "success"), userId);
    }

    public void passwordResetRejected(String reason) {
        emit(event(Level.WARN, "Password reset rejected", "password-reset", "iam", "failure")
                        .addKeyValue("event.reason", sanitize(reason)),
                null);
    }

    // --- administration -------------------------------------------------------------------

    /** {@code change} is e.g. {@code role_changed}, {@code account_enabled}, {@code account_disabled}, {@code account_deleted}. */
    public void adminChange(UUID actorId, UUID targetId, String change, String newValue) {
        LoggingEventBuilder builder = event(Level.INFO, "User administration change", "user-administration", "iam", "success")
                .addKeyValue("user.target.id", str(targetId))
                .addKeyValue("labels.change", sanitize(change));
        if (newValue != null) {
            builder = builder.addKeyValue("labels.new_value", sanitize(newValue));
        }
        emit(builder, actorId);
    }

    public void adminChangeRejected(UUID actorId, UUID targetId, String change, String reason) {
        emit(event(Level.WARN, "User administration change rejected", "user-administration", "iam", "failure")
                        .addKeyValue("user.target.id", str(targetId))
                        .addKeyValue("labels.change", sanitize(change))
                        .addKeyValue("event.reason", sanitize(reason)),
                actorId);
    }

    // --- helpers --------------------------------------------------------------------------

    private static LoggingEventBuilder event(
            Level level, String message, String action, String category, String outcome) {
        return log.atLevel(level)
                .setMessage(message)
                .addKeyValue("event.action", action)
                .addKeyValue("event.category", category)
                .addKeyValue("event.outcome", outcome);
    }

    /**
     * The actor goes into MDC as {@code user.id} for exactly this one event -- never as a key/value
     * pair -- so it can't collide with the {@code user.id} the request-context filter already put in
     * MDC, and a pre-auth event ({@code actor == null}) never inherits whoever else is logged in on
     * the same session.
     */
    private static void emit(LoggingEventBuilder builder, UUID actor) {
        String previous = MDC.get(USER_ID);
        if (actor == null) {
            MDC.remove(USER_ID);
        } else {
            MDC.put(USER_ID, actor.toString());
        }
        try {
            builder.log();
        } finally {
            if (previous == null) {
                MDC.remove(USER_ID);
            } else {
                MDC.put(USER_ID, previous);
            }
        }
    }

    private static UUID currentActor() {
        String current = MDC.get(USER_ID);
        return current == null ? null : UUID.fromString(current);
    }

    private static String str(UUID id) {
        return id == null ? null : id.toString();
    }

    /**
     * Defence in depth: every field here is either a UUID or a fixed code, but strip line
     * separators and cap the length anyway so no future caller can forge log lines or bloat the
     * audit trail through this class.
     */
    static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = UNSAFE_CHARS.matcher(value).replaceAll("_");
        return cleaned.length() > MAX_FIELD_LENGTH ? cleaned.substring(0, MAX_FIELD_LENGTH) : cleaned;
    }
}
