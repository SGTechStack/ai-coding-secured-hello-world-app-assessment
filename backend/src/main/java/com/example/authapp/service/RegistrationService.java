package com.example.authapp.service;

import com.example.authapp.domain.Role;
import com.example.authapp.domain.User;
import com.example.authapp.domain.UserRepository;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final PasswordPolicy policy;
    private final AuditLogger audit;

    public RegistrationService(UserRepository users, PasswordEncoder encoder, PasswordPolicy policy,
            AuditLogger audit) {
        this.users = users;
        this.encoder = encoder;
        this.policy = policy;
        this.audit = audit;
    }

    @Transactional
    public User register(String username, String email, String password) {
        policy.validate(password);
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new ApiException(HttpStatus.CONFLICT, "Username is already taken");
        }
        if (users.existsByEmail(normalizedEmail)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
        }
        try {
            User saved = users.saveAndFlush(
                    new User(username, normalizedEmail, encoder.encode(password), Role.USER));
            audit.log("register", "user", saved.getUsername());
            return saved;
        } catch (DataIntegrityViolationException e) {
            // Lost a race with a concurrent registration.
            throw new ApiException(HttpStatus.CONFLICT, "Username or email is already registered");
        }
    }
}
