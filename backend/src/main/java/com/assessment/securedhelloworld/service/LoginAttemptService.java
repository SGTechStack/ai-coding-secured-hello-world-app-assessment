package com.assessment.securedhelloworld.service;

import com.assessment.securedhelloworld.domain.User;
import com.assessment.securedhelloworld.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Account-level brute-force lockout (PRD Story 3 / IM8 as-4): tracks
 * {@code failed_login_attempts} per user and sets {@code locked_until} once the configured
 * threshold is reached. Deliberately account-scoped only — {@link IpLoginThrottleService} is the
 * separate, independent control that stops an attacker from forcing this lockout onto a
 * legitimate user merely by failing their password from one IP.
 */
@Service
public class LoginAttemptService {

    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final int maxAttempts;
    private final long lockoutMinutes;

    public LoginAttemptService(UserRepository userRepository,
                                AuditLogService auditLogService,
                                @Value("${app.security.lockout.max-attempts}") int maxAttempts,
                                @Value("${app.security.lockout.lockout-minutes}") long lockoutMinutes) {
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.maxAttempts = maxAttempts;
        this.lockoutMinutes = lockoutMinutes;
    }

    /**
     * Records a failed login attempt against {@code username}. No-op if the username doesn't
     * exist, so this never leaks account existence (the HTTP response is already generic; this
     * is purely internal bookkeeping) and never creates a phantom row.
     * <p>
     * If the account is already locked, this intentionally does NOT increment further or extend
     * the lock — that path is reached when a locked account is retried with the wrong password,
     * since Spring Security's pre-authentication check rejects the locked account before the
     * password is even checked, but this failure hook still fires.
     */
    public void onLoginFailure(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            if (user.isLocked()) {
                return;
            }
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
            if (user.getFailedLoginAttempts() >= maxAttempts) {
                user.setLockedUntil(Instant.now().plus(Duration.ofMinutes(lockoutMinutes)));
                auditLogService.event("account_lockout", "triggered", username, username);
            }
            userRepository.save(user);
        });
    }

    /** Clears the failure counter and any lock on a successful login (PRD Story 2/3). */
    public void onLoginSuccess(User user) {
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
    }
}
