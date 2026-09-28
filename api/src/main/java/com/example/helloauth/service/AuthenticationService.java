package com.example.helloauth.service;

import com.example.helloauth.settings.AppProperties;
import com.example.helloauth.domain.Account;
import com.example.helloauth.domain.Role;
import com.example.helloauth.repository.AccountRepository;
import com.example.helloauth.service.exception.AuthExceptions.InvalidCredentialsException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Credential verification, account lockout, and the bookkeeping that goes with both.
 *
 * <p>No servlet types appear here. This decides <em>whether</em> a login succeeds; {@link
 * SessionService} decides what a successful one does to the request.
 */
@Service
public class AuthenticationService {

    /** What the caller learns on success. Notably not the account itself, and never the hash. */
    public record AuthenticatedAccount(String username, Role role) {}

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final IpThrottleService throttle;
    private final AuditLog audit;
    private final AppProperties properties;
    private final Clock clock;

    public AuthenticationService(
            AccountRepository accounts,
            PasswordEncoder passwordEncoder,
            IpThrottleService throttle,
            AuditLog audit,
            AppProperties properties,
            Clock clock) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.throttle = throttle;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Verifies credentials and updates lockout state.
     *
     * <p>Every failure path throws the same {@link InvalidCredentialsException}, so the caller cannot
     * accidentally turn "no such account" or "account locked" into a distinguishable response. The
     * distinction is recorded in the audit log instead, where it is useful to an operator and
     * useless to an attacker.
     *
     * <p>{@code noRollbackFor} is load-bearing, not decoration. Recording a failed attempt is a
     * write, and rejecting the login is an exception; without this, the rollback would erase the
     * very counter that lockout depends on and the account would never lock.
     */
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public AuthenticatedAccount login(String username, String rawPassword, String clientIp) {
        throttle.assertNotThrottled(clientIp);

        Instant now = clock.instant();
        Optional<Account> found = accounts.findByUsername(RegistrationService.normalise(username));

        if (found.isEmpty()) {
            return reject(username, clientIp, "unknown_username");
        }
        Account account = found.get();

        if (account.isLocked(now)) {
            // Neither the counter nor the deadline moves. Extending the lockout on every attempt
            // would let an attacker hold a legitimate user out indefinitely with a trickle of junk
            // requests — the same attack IP throttling exists to prevent. Those attempts are still
            // counted against the source address, so they are not free.
            return reject(username, clientIp, "account_locked");
        }

        if (account.getLockedUntil() != null) {
            // A lockout that has expired. Clearing it here means a user who has just served one
            // starts fresh rather than one failure away from the next.
            account.clearFailedLogins();
        }

        if (!account.isEnabled()) {
            return reject(username, clientIp, "account_disabled");
        }

        if (!passwordEncoder.matches(rawPassword, account.getPasswordHash())) {
            account.recordFailedLogin();
            if (account.getFailedLoginAttempts() >= properties.lockout().maxFailedAttempts()) {
                account.lockUntil(now.plus(properties.lockout().cooldown()));
                audit.lockoutTriggered(account.getUsername(), account.getLockedUntil());
            }
            return reject(username, clientIp, "bad_password");
        }

        account.clearFailedLogins();
        audit.loginSucceeded(account.getUsername(), clientIp);
        return new AuthenticatedAccount(account.getUsername(), account.getRole());
    }

    /**
     * @return never; the return type exists only so callers can write {@code return reject(...)} and
     *     keep the control flow obvious at each call site
     */
    private AuthenticatedAccount reject(String username, String clientIp, String reason) {
        throttle.recordAttempt(clientIp);
        audit.loginFailed(username, clientIp, reason);
        throw new InvalidCredentialsException();
    }
}
