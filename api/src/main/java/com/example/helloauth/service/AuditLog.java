package com.example.helloauth.service;

import com.example.helloauth.domain.Role;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The audit trail the PRD asks for: structured log lines, no dedicated table.
 *
 * <p>Every event goes through a method here rather than being logged inline at the call site. That
 * is what keeps the field names consistent enough to grep or ship to a log aggregator, and it puts
 * the "never log a password" rule in one auditable place instead of trusting every caller.
 */
@Component
public class AuditLog {

    private static final Logger log = LoggerFactory.getLogger("audit");

    public void loginSucceeded(String username, String clientIp) {
        event("login.success", "username", username, "ip", clientIp);
    }

    /**
     * @param reason why it failed. This is the one place the distinction between "unknown
     *     username", "bad password", "locked" and "disabled" is recorded. It belongs in the log,
     *     where an operator can see it, and not in the response, where an attacker could.
     */
    public void loginFailed(String username, String clientIp, String reason) {
        event("login.failure", "username", username, "ip", clientIp, "reason", reason);
    }

    public void lockoutTriggered(String username, Instant until) {
        event("account.lockout", "username", username, "until", until);
    }

    public void throttled(String clientIp, String endpoint) {
        event("ip.throttled", "ip", clientIp, "endpoint", endpoint);
    }

    public void accountRegistered(String username, UUID accountId) {
        event("account.registered", "username", username, "accountId", accountId);
    }

    public void passwordResetRequested(String username) {
        event("password_reset.requested", "username", username);
    }

    public void passwordResetCompleted(String username, int sessionsInvalidated) {
        event(
                "password_reset.completed",
                "username",
                username,
                "sessionsInvalidated",
                sessionsInvalidated);
    }

    public void accountStatusChanged(String actor, String target, boolean enabled) {
        event("admin.account_status_changed", "actor", actor, "target", target, "enabled", enabled);
    }

    public void accountRoleChanged(String actor, String target, Role role) {
        event("admin.account_role_changed", "actor", actor, "target", target, "role", role);
    }

    public void accountDeleted(String actor, String target) {
        event("admin.account_deleted", "actor", actor, "target", target);
    }

    public void adminSeeded(String username) {
        event("admin.seeded", "username", username);
    }

    private void event(String name, Object... keyValuePairs) {
        StringBuilder line = new StringBuilder("event=").append(name);
        for (int i = 0; i + 1 < keyValuePairs.length; i += 2) {
            line.append(' ').append(keyValuePairs[i]).append('=').append(keyValuePairs[i + 1]);
        }
        log.info("{}", line);
    }
}
