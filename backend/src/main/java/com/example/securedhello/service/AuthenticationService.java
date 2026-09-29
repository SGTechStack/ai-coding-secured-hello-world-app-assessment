package com.example.securedhello.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.securedhello.config.SecurityProperties;
import com.example.securedhello.entity.User;
import com.example.securedhello.repository.UserRepository;

/**
 * Verifies login credentials and applies the two brute-force defences in the
 * fixed order <b>IP Throttle &rarr; Account Lockout &rarr; Credential
 * Check</b>.
 *
 * <ul>
 *   <li><b>Throttle</b>: if the client IP is already throttled, reject with
 *       {@link ThrottledException} (429) before touching account state.</li>
 *   <li><b>Lockout</b>: if the account's {@code locked_until} is in the future,
 *       reject with {@link AuthenticationFailedException} (401) even when the
 *       password is correct.</li>
 *   <li><b>Credential</b>: verify the BCrypt hash. On the Nth consecutive
 *       failure the account is locked for the cooldown and a lockout Audit
 *       Event is emitted. Success clears the counter and any lock.</li>
 * </ul>
 *
 * Every failure mode throws with an identical generic message (Enumeration
 * Resistance); only the HTTP status differs (429 for throttle, 401 otherwise).
 * Time is read through the injected {@link Clock}.
 */
@Service
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final IpThrottleService ipThrottleService;
    private final SecurityProperties properties;
    private final Clock clock;

    public AuthenticationService(UserRepository userRepository,
                                 PasswordEncoder passwordEncoder,
                                 AuditService auditService,
                                 IpThrottleService ipThrottleService,
                                 SecurityProperties properties,
                                 Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.ipThrottleService = ipThrottleService;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Authenticates a Username/password pair from a given client IP.
     *
     * @throws ThrottledException            if the client IP is throttled
     * @throws AuthenticationFailedException on any other failure (unknown user,
     *                                       wrong password, disabled or locked account)
     */
    @Transactional(noRollbackFor = {AuthenticationFailedException.class, ThrottledException.class})
    public User authenticate(String username, String rawPassword, String clientIp) {
        // 1. IP Throttle.
        if (ipThrottleService.isThrottled(clientIp)) {
            auditService.record("LOGIN_THROTTLED", username, null, "FAILURE");
            throw new ThrottledException();
        }

        Optional<User> maybeUser = userRepository.findByUsername(username);
        if (maybeUser.isEmpty()) {
            ipThrottleService.recordFailure(clientIp);
            auditService.record("LOGIN_FAILURE", username, null, "FAILURE");
            throw new AuthenticationFailedException();
        }
        User user = maybeUser.get();

        // 2. Account Lockout: reject while locked, even with correct credentials.
        if (isLocked(user)) {
            ipThrottleService.recordFailure(clientIp);
            auditService.record("LOGIN_FAILURE", username, null, "FAILURE");
            throw new AuthenticationFailedException();
        }

        // A disabled account is refused outright. This is not a credential
        // failure, so it must not increment the failed-attempt counter, lock
        // the account, or feed the IP throttle. The generic error preserves
        // Enumeration Resistance.
        if (!user.isEnabled()) {
            auditService.record("LOGIN_DISABLED", username, null, "FAILURE");
            throw new AuthenticationFailedException();
        }

        // 3. Credential Check.
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            registerFailure(user, clientIp);
            throw new AuthenticationFailedException();
        }

        // Success: clear counter and any lock.
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        auditService.record("LOGIN_SUCCESS", username, null, "SUCCESS");
        return user;
    }

    private boolean isLocked(User user) {
        Instant lockedUntil = user.getLockedUntil();
        return lockedUntil != null && lockedUntil.isAfter(clock.instant());
    }

    private void registerFailure(User user, String clientIp) {
        ipThrottleService.recordFailure(clientIp);
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        if (attempts >= properties.lockout().maxAttempts()) {
            user.setLockedUntil(clock.instant().plus(properties.lockout().cooldown()));
            user.setFailedLoginAttempts(0);
            userRepository.save(user);
            auditService.record("ACCOUNT_LOCKED", user.getUsername(), null, "FAILURE");
        } else {
            userRepository.save(user);
        }
        auditService.record("LOGIN_FAILURE", user.getUsername(), null, "FAILURE");
    }
}
