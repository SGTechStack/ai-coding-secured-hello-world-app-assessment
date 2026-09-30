package com.example.auth.security;

import com.example.auth.audit.AuditLogger;
import com.example.auth.security.ratelimit.RateLimiters;
import com.example.auth.user.User;
import com.example.auth.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureDisabledEvent;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account lockout and login-failure accounting (ADR-0005).
 *
 * <ul>
 *   <li>Only a wrong password ({@link AuthenticationFailureBadCredentialsEvent}, which also covers
 *       unknown usernames) counts as a failure. Because {@code SecurityConfig} checks the password
 *       before the account status, a locked/disabled account reports {@code LockedException}/
 *       {@code DisabledException} only when the password was <em>right</em> -- those are audited
 *       but never counted.
 *   <li>A failure while the account is already locked is not counted and does not extend the lock:
 *       otherwise one guess every few minutes would keep an account locked forever.
 *   <li>{@code app.security.lockout.max-attempts} consecutive failures lock the account for {@code
 *       app.security.lockout.duration}; a successful login resets the counter.
 *   <li>The counter is updated under a row lock ({@code SELECT ... FOR UPDATE}) so concurrent
 *       failures can't lose increments and grant extra guesses.
 *   <li>Each failure is also recorded against the source IP and the (IP, username) pair, which the
 *       login filter checks before authenticating.
 * </ul>
 */
@Component
public class LoginAttemptListener {

    private final UserRepository userRepository;
    private final AuditLogger auditLogger;
    private final RateLimiters rateLimiters;
    private final int maxFailedAttempts;
    private final Duration lockoutDuration;

    public LoginAttemptListener(
            UserRepository userRepository,
            AuditLogger auditLogger,
            RateLimiters rateLimiters,
            @Value("${app.security.lockout.max-attempts}") int maxFailedAttempts,
            @Value("${app.security.lockout.duration}") Duration lockoutDuration) {
        this.userRepository = userRepository;
        this.auditLogger = auditLogger;
        this.rateLimiters = rateLimiters;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockoutDuration = lockoutDuration;
    }

    @EventListener
    @Transactional
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        String username = event.getAuthentication().getName();
        boolean badCredentials = event instanceof AuthenticationFailureBadCredentialsEvent;
        Optional<User> user = username == null
                ? Optional.empty()
                : badCredentials ? userRepository.findByUsernameForUpdate(username) : userRepository.findByUsername(username);
        UUID userId = user.map(User::getPublicId).orElse(null);

        if (!badCredentials) {
            auditLogger.loginFailed(userId, reasonFor(event));
            return;
        }

        String ip = remoteAddress(event.getAuthentication());
        if (ip != null) {
            rateLimiters.loginIp().record(ip);
            rateLimiters.loginIpUsername().record(
                    RateLimiters.ipUsernameKey(ip, JsonUsernamePasswordAuthenticationFilter.limiterKey(username)));
        }
        auditLogger.loginFailed(userId, "bad_credentials");

        user.ifPresent(u -> {
            Instant now = Instant.now();
            if (u.isLocked(now)) {
                return;
            }
            if (u.getLockedUntil() != null) {
                // A previous lockout has expired: start counting fresh.
                u.setFailedLoginAttempts(0);
                u.setLockedUntil(null);
            }
            u.setFailedLoginAttempts(u.getFailedLoginAttempts() + 1);
            boolean justLocked = u.getFailedLoginAttempts() >= maxFailedAttempts;
            if (justLocked) {
                u.setLockedUntil(now.plus(lockoutDuration));
            }
            userRepository.saveAndFlush(u);
            // Logged only after the write succeeds -- the audit trail must never claim a lockout
            // that wasn't persisted.
            if (justLocked) {
                auditLogger.accountLocked(u.getPublicId());
            }
        });
    }

    @EventListener
    @Transactional
    public void onSuccess(AuthenticationSuccessEvent event) {
        Authentication authentication = event.getAuthentication();
        String username = authentication.getName();
        userRepository.findByUsernameForUpdate(username).ifPresent(user -> {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
            auditLogger.loginSucceeded(user.getPublicId());
        });
        String ip = remoteAddress(authentication);
        if (ip != null) {
            rateLimiters.loginIpUsername().clear(
                    RateLimiters.ipUsernameKey(ip, JsonUsernamePasswordAuthenticationFilter.limiterKey(username)));
        }
    }

    private static String reasonFor(AbstractAuthenticationFailureEvent event) {
        if (event instanceof AuthenticationFailureLockedEvent) {
            return "locked";
        }
        if (event instanceof AuthenticationFailureDisabledEvent) {
            return "disabled";
        }
        return "other";
    }

    private static String remoteAddress(Authentication authentication) {
        return authentication.getDetails() instanceof WebAuthenticationDetails details ? details.getRemoteAddress() : null;
    }
}
