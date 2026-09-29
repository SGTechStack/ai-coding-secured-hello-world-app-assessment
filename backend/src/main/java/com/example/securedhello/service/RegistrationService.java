package com.example.securedhello.service;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.securedhello.entity.Role;
import com.example.securedhello.entity.User;
import com.example.securedhello.repository.UserRepository;

/**
 * Registers new accounts. Enforces Username/Email uniqueness, hashes the
 * password with BCrypt, and persists a fully-formed {@code USER} account with
 * {@code enabled = true} and a zeroed failed-attempt counter.
 *
 * <p>Enumeration note: registration necessarily reveals whether a Username or
 * Email is taken (a conflict is the correct product behaviour here). The
 * plaintext password is never logged and never stored — only its BCrypt hash.
 */
@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public RegistrationService(UserRepository userRepository,
                               PasswordEncoder passwordEncoder,
                               Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /**
     * Creates a new {@code USER} account.
     *
     * @throws DuplicateAccountException if the Username or Email is already taken
     */
    @Transactional
    public User register(String username, String email, String rawPassword) {
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateAccountException("Username is already taken");
        }
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateAccountException("Email is already registered");
        }

        String passwordHash = passwordEncoder.encode(rawPassword);
        User user = new User(username, email, passwordHash, Role.USER, true, clock.instant());
        User saved = userRepository.save(user);

        // Never log the plaintext password or hash; the Username is not a secret.
        log.info("Registered new account username={} role={}", saved.getUsername(), saved.getRole());
        return saved;
    }
}
