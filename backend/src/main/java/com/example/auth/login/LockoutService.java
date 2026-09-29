package com.example.auth.login;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.example.auth.audit.AuditService;
import com.example.auth.config.SecurityProperties;
import com.example.auth.user.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional lockout operations that must run under a database row lock.
 * Extracted as a separate Spring bean so that @Transactional is effective
 * (Spring's proxy only intercepts calls from outside the bean).
 *
 * Every public method acquires a PESSIMISTIC_WRITE lock via
 * UserRepository.findByUsernameWithLock, ensuring that concurrent failed-login
 * recordings serialise at the database level and cannot bypass the lockout
 * threshold via a lost-update race (Story 45).
 */
@Service
public class LockoutService {

    private final UserRepository users;
    private final AuditService audit;
    private final Clock clock;
    private final int maxAttempts;
    private final int durationMinutes;

    public LockoutService(UserRepository users,
                          AuditService audit,
                          Clock clock,
                          SecurityProperties props) {
        this.users = users;
        this.audit = audit;
        this.clock = clock;
        SecurityProperties.LockoutConfig cfg = props.lockout();
        this.maxAttempts = cfg.maxAttempts();
        this.durationMinutes = cfg.durationMinutes();
    }

    /**
     * Called on every successful authentication. Resets failed_login_attempts
     * and clears any expired lockout under a write lock to prevent a concurrent
     * failure from immediately re-locking the just-unlocking account.
     */
    @Transactional
    public void onSuccess(String username) {
        users.findByUsernameWithLock(username).ifPresent(user -> user.resetFailedLogins());
    }

    /**
     * Called on every authentication failure for an existing account (unknown
     * usernames have no row to lock). Increments the counter and, if the
     * threshold is reached, locks the account by setting locked_until.
     * The write lock ensures no two concurrent failures can both read the same
     * pre-threshold counter and both decide not to lock.
     */
    @Transactional
    public void onFailure(String username) {
        users.findByUsernameWithLock(username).ifPresent(user -> {
            user.recordFailedLogin();
            if (user.getFailedLoginAttempts() >= maxAttempts) {
                Instant lockUntil = clock.instant().plus(durationMinutes, ChronoUnit.MINUTES);
                user.lockUntil(lockUntil);
                audit.accountLocked(username);
            }
        });
    }
}
