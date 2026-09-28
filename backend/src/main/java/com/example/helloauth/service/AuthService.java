package com.example.helloauth.service;

import com.example.helloauth.domain.User;
import com.example.helloauth.repo.UserRepository;
import com.example.helloauth.security.AuditLogger;
import com.example.helloauth.security.IpThrottleService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Authenticates users. Deliberately NOT @Transactional at the method boundary: attempt outcomes
 * are persisted via LoginAttemptService in independent (REQUIRES_NEW) transactions so that a
 * failed-attempt increment / lockout survives the AuthenticationFailedException that this method
 * throws on failure.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final IpThrottleService ipThrottle;
    private final LoginAttemptService loginAttempts;
    private final AuditLogger audit;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       IpThrottleService ipThrottle, LoginAttemptService loginAttempts,
                       AuditLogger audit) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.ipThrottle = ipThrottle;
        this.loginAttempts = loginAttempts;
        this.audit = audit;
    }

    public User authenticate(String username, String rawPassword, String ip) {
        // IP-level throttle first, independent of any single account state (IM8 as-4).
        if (ipThrottle.isThrottled(ip)) {
            audit.ipThrottled(ip);
            throw new ServiceExceptions.ThrottledException();
        }

        Optional<User> maybeUser = userRepository.findByUsername(username);

        // Unknown username: dummy hash comparison to blunt timing side-channels, then fail generically.
        if (maybeUser.isEmpty()) {
            passwordEncoder.matches(rawPassword,
                    "$2a$10$0000000000000000000000000000000000000000000000000000");
            loginAttempts.recordFailure(null, ip, "unknown_username", username);
            throw new ServiceExceptions.AuthenticationFailedException();
        }

        User user = maybeUser.get();

        // Locked account: reject even with correct credentials until lockout expires.
        if (user.isCurrentlyLocked()) {
            loginAttempts.recordFailure(user.getId(), ip, "account_locked", username);
            throw new ServiceExceptions.AuthenticationFailedException();
        }

        // Disabled account cannot log in.
        if (!user.isEnabled()) {
            loginAttempts.recordFailure(user.getId(), ip, "account_disabled", username);
            throw new ServiceExceptions.AuthenticationFailedException();
        }

        // Wrong password.
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            loginAttempts.recordFailure(user.getId(), ip, "bad_credentials", username);
            throw new ServiceExceptions.AuthenticationFailedException();
        }

        // Success: reset counters, clear lockout, record login.
        loginAttempts.recordSuccess(user.getId(), ip, username);
        // Return a fresh copy reflecting the committed success state.
        return userRepository.findById(user.getId()).orElse(user);
    }
}
