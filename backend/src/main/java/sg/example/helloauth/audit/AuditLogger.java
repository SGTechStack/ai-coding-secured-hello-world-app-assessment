package sg.example.helloauth.audit;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.stereotype.Component;

import sg.example.helloauth.api.ClientIpResolver;
import sg.example.helloauth.logging.LogSanitizer;

/**
 * The only component that logs security events. Each method is one auditable event, written to
 * the {@code audit} logger as ECS key-value fields, with the request's method and path. Accounts
 * appear only as their UUID, and never on a failed login, where the Account may not exist.
 * Client IPs appear only as a keyed hash, so events from one source can be linked without
 * storing the address. Callers pass no free text, so no user input reaches a log field.
 */
@Component
public class AuditLogger {

    private static final Logger audit = LoggerFactory.getLogger("audit");

    private static final String HMAC = "HmacSHA256";

    private static final String USER_ID = "user.id";

    /** The Account an Admin acted on; {@code user.id} is the Admin. */
    private static final String TARGET_USER_ID = "user.target.id";

    private static final String USER_ADMINISTRATION = "user-administration";

    /** The one way anyone logs in here. */
    private static final String AUTH_METHOD = "password";

    private static final String PASSWORD_RESET = "password-reset";

    private static final Failure REGISTRATION_CLASH = new Failure(400, "application", "medium", false);
    private static final Failure RESET_TOKEN_REJECTED = new Failure(400, "cert/auth", "medium", false);
    private static final Failure WRONG_CREDENTIALS = new Failure(401, "cert/auth", "medium", false);
    /** Someone may be guessing the password: worth a look. */
    private static final Failure LOCKED = new Failure(423, "cert/auth", "high", true);
    private static final Failure THROTTLED = new Failure(429, "others", "medium", false);
    private static final Failure DENIED = new Failure(403, "cert/auth", "medium", false);
    private static final Failure SELF_ACTION = new Failure(403, "application", "medium", false);
    private static final Failure ADMIN_TARGET_NOT_FOUND = new Failure(404, "application", "low", false);

    private final SecretKeySpec ipHashKey;
    private final ClientIpResolver clientIps;

    AuditLogger(AuditProperties properties, ClientIpResolver clientIps) {
        this.ipHashKey = new SecretKeySpec(properties.ipHashSecret().getBytes(StandardCharsets.UTF_8), HMAC);
        this.clientIps = clientIps;
    }

    public void registered(UUID accountId, HttpServletRequest request) {
        log(success("Account registered.", "user-provisioning", "creation", request), accountId);
    }

    /** Created at startup from the operator's configuration, not by any request. */
    public void bootstrapAdminCreated(UUID accountId) {
        log(success("Bootstrap admin created.", "user-provisioning", "creation", null), accountId);
    }

    /** The username or email was taken. Which one isn't recorded, and neither is who tried. */
    public void registrationRejected(HttpServletRequest request) {
        log(failure("Registration rejected.", "user-provisioning", "creation", REGISTRATION_CLASH, request), null);
    }

    public void loginSucceeded(UUID accountId, HttpServletRequest request) {
        log(success("Authentication succeeded.", "user-authentication", "user", request)
                .addKeyValue("auth.method", AUTH_METHOD), accountId);
    }

    /** Every failed login looks the same: no account identity, whatever the reason. */
    public void loginFailed(HttpServletRequest request) {
        log(failure("Authentication failed.", "user-authentication", "user", WRONG_CREDENTIALS, request)
                .addKeyValue("auth.method", AUTH_METHOD), null);
    }

    public void loggedOut(UUID accountId, HttpServletRequest request) {
        log(success("User logged out.", "user-logout", "end", request), accountId);
    }

    /** A handled condition rather than a system failure, so WARN, not ERROR (ADR-0009). */
    public void accountLocked(UUID accountId, HttpServletRequest request) {
        log(failure("Account locked after repeated failed logins.", "ATTEMPTS_EXCEEDED", "change", LOCKED, request),
                accountId);
    }

    /** A request was Throttled. Like a failed login, it names no Account: only the hashed IP. */
    public void throttled(HttpServletRequest request) {
        log(failure("Request throttled.", "access-control", "denied", THROTTLED, request), null);
    }

    /** A request was refused with 403. It names the caller when there is one. */
    public void accessDenied(UUID callerId, HttpServletRequest request) {
        log(failure("Access denied.", "access-control", "denied", DENIED, request), callerId);
    }

    /**
     * Someone asked for a reset link. Whether the email belongs to an Account is decided later,
     * in the background, so the event looks the same either way and names no Account.
     */
    public void passwordResetRequested(HttpServletRequest request) {
        log(success("Password reset requested.", PASSWORD_RESET, "start", request), null);
    }

    /**
     * A token was issued to this Account, in the background after the request was answered. No
     * request is at hand by then; its correlation ID comes along in the MDC.
     */
    public void passwordResetTokenIssued(UUID accountId) {
        log(success("Password reset token issued.", PASSWORD_RESET, "creation", null), accountId);
    }

    public void passwordResetCompleted(UUID accountId, HttpServletRequest request) {
        log(success("Password reset completed.", PASSWORD_RESET, "change", request), accountId);
    }

    /** The token was unknown, used or expired: there is no Account it can be said to belong to. */
    public void passwordResetRejected(HttpServletRequest request) {
        log(failure("Password reset rejected.", PASSWORD_RESET, "change", RESET_TOKEN_REJECTED, request), null);
    }

    public void accountDisabled(UUID adminId, UUID targetId, HttpServletRequest request) {
        administered("Account disabled.", "change", adminId, targetId, request);
    }

    public void accountEnabled(UUID adminId, UUID targetId, HttpServletRequest request) {
        administered("Account enabled.", "change", adminId, targetId, request);
    }

    public void accountUnlocked(UUID adminId, UUID targetId, HttpServletRequest request) {
        administered("Account unlocked.", "change", adminId, targetId, request);
    }

    /** @param newRole the role's name, e.g. {@code ADMIN} */
    public void roleChanged(UUID adminId, UUID targetId, String newRole, HttpServletRequest request) {
        log(success("Account role changed.", USER_ADMINISTRATION, "change", request)
                .addKeyValue(TARGET_USER_ID, targetId.toString())
                .addKeyValue("user.target.roles", List.of(newRole)), adminId);
    }

    /** The Account became a Tombstone. */
    public void accountDeleted(UUID adminId, UUID targetId, HttpServletRequest request) {
        administered("Account deleted.", "deletion", adminId, targetId, request);
    }

    /** An Admin tried to change their own Account, which is never allowed. */
    public void selfAdministrationRejected(UUID adminId, HttpServletRequest request) {
        administrationRejected(SELF_ACTION, adminId, adminId, request);
    }

    /** The target is a UUID the Admin sent, never free text; no active Account has it. */
    public void administrationTargetNotFound(UUID adminId, UUID targetId, HttpServletRequest request) {
        administrationRejected(ADMIN_TARGET_NOT_FOUND, adminId, targetId, request);
    }

    private void administrationRejected(Failure failure, UUID adminId, UUID targetId, HttpServletRequest request) {
        log(failure("Account administration rejected.", USER_ADMINISTRATION, "change", failure, request)
                .addKeyValue(TARGET_USER_ID, targetId.toString()), adminId);
    }

    private void administered(String message, String type, UUID adminId, UUID targetId, HttpServletRequest request) {
        log(success(message, USER_ADMINISTRATION, type, request).addKeyValue(TARGET_USER_ID, targetId.toString()),
                adminId);
    }

    /**
     * Writes the event, naming its Account if it has one. The request's own {@code user.id} is
     * kept off the line: ECS allows one {@code user.id} per line (the encoder drops a line that
     * repeats a key), and a failed login must carry none at all.
     */
    private static void log(LoggingEventBuilder event, UUID accountId) {
        if (accountId != null) {
            event = event.addKeyValue(USER_ID, accountId.toString());
        }
        String requestUserId = MDC.get(USER_ID);
        MDC.remove(USER_ID);
        try {
            event.log();
        } finally {
            if (requestUserId != null) {
                MDC.put(USER_ID, requestUserId);
            }
        }
    }

    private LoggingEventBuilder success(String message, String action, String type, HttpServletRequest request) {
        return event(audit.atInfo(), message, action, type, "success", "low", request);
    }

    private LoggingEventBuilder failure(String message, String action, String type, Failure failure,
            HttpServletRequest request) {
        return event(audit.atWarn(), message, action, type, "failure", failure.severity(), request)
                .addKeyValue("error_code", failure.code())
                .addKeyValue("error_category", failure.category())
                .addKeyValue("error_follow_up_action", failure.followUp());
    }

    /**
     * Each key goes on once: the ECS encoder drops a line that repeats one. An event that no
     * request caused, such as one at startup, has no request fields.
     */
    private LoggingEventBuilder event(LoggingEventBuilder builder, String message, String action, String type,
            String outcome, String severity, HttpServletRequest request) {
        LoggingEventBuilder event = builder.setMessage(message)
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", List.of("process"))
                .addKeyValue("event.type", List.of(type))
                .addKeyValue("event.action", action)
                .addKeyValue("event.outcome", outcome)
                .addKeyValue("event.severity", severity);
        if (request == null) {
            return event;
        }
        return event.addKeyValue("http.request.method", request.getMethod())
                .addKeyValue("url.path", LogSanitizer.strip(request.getRequestURI()))
                .addKeyValue("source.ip_hash", hash(clientIps.clientIp(request)));
    }

    private String hash(String ip) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(ipHashKey);
            return HexFormat.of().formatHex(mac.doFinal(ip.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", ex);
        }
    }

    /** The error fields of a failure event, per the log schema's {@code error.*} fields. */
    private record Failure(int code, String category, String severity, boolean followUp) {
    }
}
