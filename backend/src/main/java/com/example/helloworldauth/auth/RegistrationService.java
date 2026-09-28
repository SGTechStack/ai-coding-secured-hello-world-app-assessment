package com.example.helloworldauth.auth;

import com.example.helloworldauth.user.Role;
import com.example.helloworldauth.user.User;
import com.example.helloworldauth.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    private static final Logger audit = LoggerFactory.getLogger("audit");

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Creates a USER account with a BCrypt-hashed password. Rejects duplicate
     * username/email. The plaintext password is never logged or stored.
     */
    @Transactional
    public User register(RegisterRequest request) {
        if (users.existsByUsername(request.username())) {
            throw new RegistrationConflictException("Username already registered");
        }
        if (users.existsByEmail(request.email())) {
            throw new RegistrationConflictException("Email already registered");
        }

        String hash = passwordEncoder.encode(request.password());
        User saved = users.save(new User(request.username(), request.email(), hash, Role.USER));
        audit.info("registration success username={}", saved.getUsername());
        return saved;
    }
}
