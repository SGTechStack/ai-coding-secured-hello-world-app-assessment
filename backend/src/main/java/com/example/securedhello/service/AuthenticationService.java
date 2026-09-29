package com.example.securedhello.service;

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.securedhello.entity.User;
import com.example.securedhello.repository.UserRepository;

/**
 * Verifies login credentials and maintains the per-account failed-attempt
 * counter. On success the counter resets to zero; on failure it increments.
 * Every failure path throws {@link AuthenticationFailedException} with an
 * identical generic message so the caller cannot distinguish unknown-username
 * from wrong-password (Enumeration Resistance).
 *
 * <p>Account Lockout (issue 06) will extend this class; the counter and the
 * fixed failure ordering established here are its foundation.
 */
@Service
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public AuthenticationService(UserRepository userRepository,
                                 PasswordEncoder passwordEncoder,
                                 AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    /**
     * Authenticates a Username/password pair.
     *
     * @return the authenticated {@link User}
     * @throws AuthenticationFailedException on any failure (unknown user,
     *                                       wrong password, disabled account)
     */
    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public User authenticate(String username, String rawPassword) {
        Optional<User> maybeUser = userRepository.findByUsername(username);
        if (maybeUser.isEmpty()) {
            auditService.record("LOGIN_FAILURE", username, null, "FAILURE");
            throw new AuthenticationFailedException();
        }

        User user = maybeUser.get();
        if (!user.isEnabled() || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
            userRepository.save(user);
            auditService.record("LOGIN_FAILURE", username, null, "FAILURE");
            throw new AuthenticationFailedException();
        }

        user.setFailedLoginAttempts(0);
        userRepository.save(user);
        auditService.record("LOGIN_SUCCESS", username, null, "SUCCESS");
        return user;
    }
}
