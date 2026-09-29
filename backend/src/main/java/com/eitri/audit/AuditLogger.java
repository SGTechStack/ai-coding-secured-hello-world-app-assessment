package com.eitri.audit;

import com.eitri.logging.SanitizedLogException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.stereotype.Component;

/**
 * Writes security audit events without accepting credentials, request bodies, or other secrets. An
 * account is named by id and stored username ({@code user.id}/{@code user.name}; admin actions use
 * {@code actor.*} and {@code target.*}), and only when the account exists.
 */
@Component
public final class AuditLogger {

    private static final Logger AUDIT = LoggerFactory.getLogger("AUDIT");

    private final SessionHasher sessionHasher;

    public AuditLogger(SessionHasher sessionHasher) {
        this.sessionHasher = sessionHasher;
    }

    public void loginSucceeded(AuditAccount user, HttpServletRequest request) {
        authenticationEvent(AUDIT.atInfo(), "success", user, request)
                .setMessage("User authentication succeeded")
                .log();
    }

    /** {@code knownUser} is null when no account has the submitted username. */
    public void loginFailed(AuditAccount knownUser, HttpServletRequest request) {
        authenticationEvent(AUDIT.atWarn(), "failure", knownUser, request)
                .setMessage("User authentication failed")
                .log();
    }

    public void loginSystemFailed(AuditAccount knownUser, HttpServletRequest request, Throwable failure) {
        authenticationEvent(AUDIT.atError(), "failure", knownUser, request)
                .setCause(SanitizedLogException.from(failure, "Authentication system failure"))
                .addKeyValue("error_code", 500)
                .addKeyValue("error_category", "database")
                .addKeyValue("error_follow_up_action", true)
                .setMessage("Authentication system failure")
                .log();
    }

    public void loginRateLimited(String sourceIp, HttpServletRequest request) {
        LoggingEventBuilder event = AUDIT.atWarn()
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", "authentication")
                .addKeyValue("event.action", "rate-limit")
                .addKeyValue("event.outcome", "failure")
                .addKeyValue("source.ip", sourceIp);
        sessionHasher.hash(request.getSession(false)).ifPresent(hash -> event.addKeyValue("session.hash", hash));
        event.setMessage("Login rate limit exceeded").log();
    }

    public void accountLocked(AuditAccount user, HttpServletRequest request) {
        LoggingEventBuilder event = user(AUDIT.atWarn(), user)
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", "authentication")
                .addKeyValue("event.action", "account-lockout")
                .addKeyValue("event.outcome", "failure");
        sessionHasher.hash(request.getSession(false)).ifPresent(hash -> event.addKeyValue("session.hash", hash));
        event.setMessage("Account locked after failed authentication attempts").log();
    }

    /** Never receives the token: only the account it was issued for. */
    public void passwordResetRequested(AuditAccount user) {
        user(iamEvent("password-reset-request"), user)
                .setMessage("Password reset requested")
                .log();
    }

    public void passwordResetCompleted(AuditAccount user) {
        user(iamEvent("password-reset-complete"), user)
                .setMessage("Password reset completed")
                .log();
    }

    public void userEnabled(AuditAccount actor, AuditAccount target) {
        adminEvent("user-enable", actor, target).setMessage("User account enabled").log();
    }

    public void userDisabled(AuditAccount actor, AuditAccount target) {
        adminEvent("user-disable", actor, target).setMessage("User account disabled").log();
    }

    public void userRoleChanged(AuditAccount actor, AuditAccount target, String fromRole, String toRole) {
        adminEvent("user-role-change", actor, target)
                .addKeyValue("role.from", fromRole)
                .addKeyValue("role.to", toRole)
                .setMessage("User role changed")
                .log();
    }

    public void userDeleted(AuditAccount actor, AuditAccount target) {
        adminEvent("user-delete", actor, target).setMessage("User account deleted").log();
    }

    /** Admin actions name both accounts: the admin as actor.id/actor.name, the account acted on as target.*. */
    private LoggingEventBuilder adminEvent(String action, AuditAccount actor, AuditAccount target) {
        return iamEvent(action)
                .addKeyValue("actor.id", actor.id().toString())
                .addKeyValue("actor.name", actor.username())
                .addKeyValue("target.id", target.id().toString())
                .addKeyValue("target.name", target.username());
    }

    /** A successful account-management (ECS {@code iam}) event. */
    private LoggingEventBuilder iamEvent(String action) {
        return AUDIT.atInfo()
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", "iam")
                .addKeyValue("event.action", action)
                .addKeyValue("event.outcome", "success");
    }

    public void logoutSucceeded(AuditAccount user, HttpServletRequest request) {
        LoggingEventBuilder event = user(AUDIT.atInfo(), user)
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", "authentication")
                .addKeyValue("event.action", "user-logout")
                .addKeyValue("event.outcome", "success");
        sessionHasher.hash(request.getSession(false)).ifPresent(hash -> event.addKeyValue("session.hash", hash));
        event.setMessage("User logged out").log();
    }

    public void absoluteSessionExpired(AuditAccount knownUser, jakarta.servlet.http.HttpSession session) {
        sessionExpiredEvent("absolute-timeout", knownUser)
                .addKeyValue("session.hash", sessionHasher.hash(session.getId()))
                .setMessage("Session expired")
                .log();
    }

    public void concurrentSessionExpired(AuditAccount user, String sessionId) {
        sessionExpiredEvent("concurrent-login", user)
                .addKeyValue("session.hash", sessionHasher.hash(sessionId))
                .setMessage("Session expired")
                .log();
    }

    private LoggingEventBuilder sessionExpiredEvent(String reason, AuditAccount knownUser) {
        return user(AUDIT.atWarn(), knownUser)
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", "authentication")
                .addKeyValue("event.action", "session-expired")
                .addKeyValue("event.outcome", "failure")
                .addKeyValue("reason", reason);
    }

    private LoggingEventBuilder authenticationEvent(
            LoggingEventBuilder event, String outcome, AuditAccount knownUser, HttpServletRequest request) {
        user(event, knownUser)
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", "authentication")
                .addKeyValue("event.action", "user-authentication")
                .addKeyValue("event.outcome", outcome);
        sessionHasher.hash(request.getSession(false)).ifPresent(hash -> event.addKeyValue("session.hash", hash));
        // Structured logging includes MDC values, including trace.id when tracing has populated it.
        return event;
    }

    /** Adds {@code user.id} and {@code user.name} for a known account; nothing for an unknown one. */
    private static LoggingEventBuilder user(LoggingEventBuilder event, AuditAccount user) {
        if (user != null) {
            event.addKeyValue("user.id", user.id().toString()).addKeyValue("user.name", user.username());
        }
        return event;
    }
}
