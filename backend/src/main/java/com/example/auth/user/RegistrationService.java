package com.example.auth.user;

import java.time.Clock;

import com.example.auth.audit.AuditService;
import com.example.auth.exception.ConflictException;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final Clock clock;

    public RegistrationService(UserRepository users, PasswordEncoder passwordEncoder,
                               AuditService audit, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public UserResponse register(RegistrationRequest req) {
        if (users.existsByUsername(req.username())) {
            throw new ConflictException("Username is already taken");
        }
        if (users.existsByEmail(req.email())) {
            throw new ConflictException("Email is already registered");
        }

        User user = new User(
                req.username(),
                req.email(),
                passwordEncoder.encode(req.password()),
                Role.USER,
                clock.instant());

        User saved = users.save(user);
        audit.registrationSuccess(saved.getUsername());
        return UserResponse.from(saved);
    }
}
