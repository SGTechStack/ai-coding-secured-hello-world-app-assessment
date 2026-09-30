package com.assessment.securedhelloworld.service;

import com.assessment.securedhelloworld.domain.Role;
import com.assessment.securedhelloworld.domain.User;
import com.assessment.securedhelloworld.exception.ApiException;
import com.assessment.securedhelloworld.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class UserRegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final int passwordMinLength;

    public UserRegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuditLogService auditLogService,
                                    @Value("${app.security.password-min-length}") int passwordMinLength) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.passwordMinLength = passwordMinLength;
    }

    @Transactional
    public User register(String username, String email, String password) {
        // Plaintext password is validated and hashed here, then discarded — it is never logged or
        // persisted on any path, success or failure (IM8 lm-19).
        if (password.length() < passwordMinLength) {
            auditLogService.event("registration", "failure", username, null, Map.of("reason", "weak_password"));
            throw new ApiException(HttpStatus.BAD_REQUEST, "WEAK_PASSWORD",
                    "Password must be at least " + passwordMinLength + " characters long");
        }
        if (userRepository.existsByUsername(username)) {
            auditLogService.event("registration", "failure", username, null, Map.of("reason", "username_taken"));
            // Deliberate, bounded exception to enumeration resistance: a registration form
            // cannot function without telling the visitor which field conflicted (spec.md
            // waiver register note). Login and password reset remain strict.
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "Username is already registered");
        }
        if (userRepository.existsByEmail(email)) {
            auditLogService.event("registration", "failure", username, null, Map.of("reason", "email_taken"));
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email is already registered");
        }

        User user = new User(username, email, passwordEncoder.encode(password), Role.USER);
        userRepository.save(user);
        auditLogService.event("registration", "success", username, username);
        return user;
    }
}
