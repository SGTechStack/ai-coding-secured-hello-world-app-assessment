package com.example.auth.security;

import com.example.auth.audit.AuditLogger;
import com.example.auth.user.User;
import com.example.auth.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account lockout: {@code app.security.lockout.max-attempts} consecutive
 * failed login attempts within {@code app.security.lockout.window} locks the
 * account for {@code app.security.lockout.duration}; a successful login
 * resets the counter. Backed by {@code User#failedLoginAttempts}/{@code
 * failedLoginWindowStart}/{@code lockedUntil}, read by {@link
 * AppUserDetailsService} on the next login attempt. The lockout is a fixed
 * cooldown: attempts made while it is running are rejected but neither
 * counted nor allowed to extend it.
 *
 * <p>One source IP can never supply all of those failures by itself -- {@link
 * IpLoginThrottleService} blocks an IP one failure short of the threshold
 * for any single username -- so a lockout means failures arrived from more
 * than one source.
 *
 * <p>Listens for {@link AbstractAuthenticationFailureEvent} rather than only
 * {@code AuthenticationFailureBadCredentialsEvent}: once an account is
 * already locked, {@code DaoAuthenticationProvider}'s pre-authentication
 * check rejects it with {@code LockedException} (a different failure event)
 * <em>before</em> comparing the password, so listening broadly keeps those
 * attempts in the audit log -- though this handler only acts on
 * events for a username that actually resolves to a {@link User} row; an
 * unknown username (also collapsed into a generic failure by {@code
 * hideUserNotFoundExceptions}) has no row to update and is a no-op.
 */
@Component
public class LoginAttemptListener {

    private final UserRepository userRepository;
    private final AuditLogger auditLogger;
    private final int maxFailedAttempts;
    private final Duration lockoutWindow;
    private final Duration lockoutDuration;

    public LoginAttemptListener(
            UserRepository userRepository,
            AuditLogger auditLogger,
            @Value("${app.security.lockout.max-attempts}") int maxFailedAttempts,
            @Value("${app.security.lockout.window}") Duration lockoutWindow,
            @Value("${app.security.lockout.duration}") Duration lockoutDuration) {
        this.userRepository = userRepository;
        this.auditLogger = auditLogger;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockoutWindow = lockoutWindow;
        this.lockoutDuration = lockoutDuration;
    }

    @EventListener
    @Transactional
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        String username = event.getAuthentication().getName();
        auditLogger.loginFailure(username);
        userRepository.findByUsername(username).ifPresent(user -> {
            Instant now = Instant.now();
            if (user.isLocked(now)) {
                // The cooldown is a fixed period from the failure that
                // triggered it. Counting (and re-locking on) attempts made
                // while it is running would let anyone who keeps retrying --
                // the owner included -- hold the account locked forever.
                return;
            }
            if (user.getLockedUntil() != null) {
                // A previous lockout has expired: start counting fresh
                // rather than immediately re-locking on the next failure.
                user.setFailedLoginAttempts(0);
                user.setLockedUntil(null);
                user.setFailedLoginWindowStart(null);
            }
            Instant windowStart = user.getFailedLoginWindowStart();
            if (windowStart == null || now.isAfter(windowStart.plus(lockoutWindow))) {
                // Only failures inside one window count toward a lockout;
                // older ones have aged out.
                user.setFailedLoginAttempts(0);
                user.setFailedLoginWindowStart(now);
            }

            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
            boolean justLocked = user.getFailedLoginAttempts() >= maxFailedAttempts;
            if (justLocked) {
                user.setLockedUntil(now.plus(lockoutDuration));
            }
            userRepository.save(user);
            // Logged only after save() succeeds -- audit_locked must never
            // claim a lockout that the DB write didn't actually persist.
            if (justLocked) {
                auditLogger.accountLocked(username);
            }
        });
    }

    @EventListener
    @Transactional
    public void onSuccess(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        auditLogger.loginSuccess(username);
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            user.setFailedLoginWindowStart(null);
            userRepository.save(user);
        });
    }
}
