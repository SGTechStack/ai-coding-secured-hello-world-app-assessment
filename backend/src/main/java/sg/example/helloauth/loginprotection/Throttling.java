package sg.example.helloauth.loginprotection;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Component;

import sg.example.helloauth.account.Account;
import sg.example.helloauth.api.ClientIpResolver;
import sg.example.helloauth.audit.AuditLogger;

/**
 * Login protection's second layer, independent of any Account's state: a client is Throttled
 * after too many failed logins from its IP, too many login attempts for one username, too many
 * registrations from its IP (ADR-0003), or too many password reset requests or confirms.
 */
@Component
public class Throttling {

    /** No username is longer, so a longer one is still throttled but can't grow the key. */
    private static final int MAX_USERNAME_KEY_LENGTH = 64;
    private static final int MAX_EMAIL_KEY_LENGTH = 254;

    private final ClientIpResolver clientIps;
    private final AuditLogger audit;
    private final Throttle failedLoginsPerIp;
    private final Throttle loginsPerUsername;
    private final Throttle registrationsPerIp;
    private final Throttle passwordResetRequestsPerIp;
    private final Throttle passwordResetRequestsPerEmail;
    private final Throttle passwordResetConfirmsPerIp;

    Throttling(ClientIpResolver clientIps, AuditLogger audit, LoginProtectionProperties properties, Clock clock) {
        this.clientIps = clientIps;
        this.audit = audit;
        LoginProtectionProperties.Throttle limits = properties.throttle();
        this.failedLoginsPerIp = new Throttle(limits.failedLoginsPerIp(), clock);
        this.loginsPerUsername = new Throttle(limits.loginsPerUsername(), clock);
        this.registrationsPerIp = new Throttle(limits.registrationsPerIp(), clock);
        this.passwordResetRequestsPerIp = new Throttle(limits.passwordResetRequestsPerIp(), clock);
        this.passwordResetRequestsPerEmail = new Throttle(limits.passwordResetRequestsPerEmail(), clock);
        this.passwordResetConfirmsPerIp = new Throttle(limits.passwordResetConfirmsPerIp(), clock);
    }

    /**
     * Whether a login attempt may go ahead: empty if so, otherwise how long until it may. The
     * username bucket is keyed on the username as submitted, never on a looked-up Account, so a
     * 429 says nothing about whether the Account exists. It ignores case, like login does, so
     * varying the case doesn't earn more attempts.
     */
    public Optional<Duration> admitLogin(String submittedUsername, HttpServletRequest request) {
        String ip = clientIps.clientIp(request);
        Optional<Duration> wait = failedLoginsPerIp.tryAcquire(ip);
        if (wait.isEmpty()) {
            wait = loginsPerUsername.tryAcquire(usernameKey(submittedUsername));
            if (wait.isPresent()) {
                // This attempt never happens, so it can't fail.
                failedLoginsPerIp.release(ip);
            }
        }
        wait.ifPresent(retryAfter -> audit.throttled(request));
        return wait;
    }

    /** Whether a registration attempt may go ahead: empty if so, otherwise how long until it may. */
    public Optional<Duration> admitRegistration(HttpServletRequest request) {
        return admitPerIp(registrationsPerIp, request);
    }

    /** Whether a request for a reset link may go ahead, judged by its client IP alone. */
    public Optional<Duration> admitPasswordResetRequest(HttpServletRequest request) {
        return admitPerIp(passwordResetRequestsPerIp, request);
    }

    /**
     * Whether a reset link may be requested for this email. Like the username bucket, it is keyed
     * on the email as submitted (ignoring case), never on a looked-up Account, so a 429 says
     * nothing about whether the email is registered.
     */
    public Optional<Duration> admitPasswordResetEmail(String submittedEmail, HttpServletRequest request) {
        Optional<Duration> wait = passwordResetRequestsPerEmail.tryAcquire(emailKey(submittedEmail));
        wait.ifPresent(retryAfter -> audit.throttled(request));
        return wait;
    }

    /** Whether an attempt to redeem a Password reset token may go ahead. */
    public Optional<Duration> admitPasswordResetConfirm(HttpServletRequest request) {
        return admitPerIp(passwordResetConfirmsPerIp, request);
    }

    private Optional<Duration> admitPerIp(Throttle throttle, HttpServletRequest request) {
        Optional<Duration> wait = throttle.tryAcquire(clientIps.clientIp(request));
        wait.ifPresent(retryAfter -> audit.throttled(request));
        return wait;
    }

    /** Only failures count against the client's IP, so a successful login gives its attempt back. */
    public void loginSucceeded(HttpServletRequest request) {
        failedLoginsPerIp.release(clientIps.clientIp(request));
    }

    private static String usernameKey(String submittedUsername) {
        return bounded(Account.usernameKey(Objects.requireNonNullElse(submittedUsername, "")), MAX_USERNAME_KEY_LENGTH);
    }

    /** Validation has already capped the email's length. */
    private static String emailKey(String submittedEmail) {
        return bounded(Account.normaliseEmail(submittedEmail), MAX_EMAIL_KEY_LENGTH);
    }

    private static String bounded(String key, int maxLength) {
        return key.length() > maxLength ? key.substring(0, maxLength) : key;
    }
}
