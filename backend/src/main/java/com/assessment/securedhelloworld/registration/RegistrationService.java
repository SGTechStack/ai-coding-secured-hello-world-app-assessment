package com.assessment.securedhelloworld.registration;

import com.assessment.securedhelloworld.user.User;
import com.assessment.securedhelloworld.user.UserRepository;
import com.assessment.securedhelloworld.logging.LogSanitizer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * New-account creation: enforces username/email uniqueness and password
 * strength, hashes the password before persisting, and records a
 * business-event metric on success. Never logs the plaintext password.
 */
@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Counter registrationCounter;

    public RegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder, MeterRegistry meterRegistry) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.registrationCounter = Counter.builder("app.registration.completed")
                .description("Successful new-account registrations")
                .register(meterRegistry);
    }

    @Transactional
    public User register(RegistrationRequest request) {
        // Deliberately never log request.getPassword() (or any derivative
        // of it) anywhere in this method, including in exception messages.
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateAccountException("Username is already registered");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateAccountException("Email is already registered");
        }

        String passwordHash = passwordEncoder.encode(request.getPassword());
        User user = new User(request.getUsername(), request.getEmail(), passwordHash);
        User saved = userRepository.save(user);

        registrationCounter.increment();
        log.info("Registered new user username={}", LogSanitizer.sanitize(saved.getUsername()));
        return saved;
    }
}
