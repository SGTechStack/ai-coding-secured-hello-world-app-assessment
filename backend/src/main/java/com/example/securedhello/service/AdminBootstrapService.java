package com.example.securedhello.service;

import java.time.Clock;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.securedhello.config.AdminProperties;
import com.example.securedhello.entity.Role;
import com.example.securedhello.entity.User;
import com.example.securedhello.repository.UserRepository;

/**
 * Seeds an initial ADMIN account so the admin module is reachable on a fresh
 * deployment without manual DB edits. Idempotent: if any ADMIN already exists,
 * seeding does nothing. The password is hashed with the same BCrypt encoder as
 * any other account. Runs on startup via {@link ApplicationRunner}.
 */
@Service
public class AdminBootstrapService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties adminProperties;
    private final Clock clock;

    public AdminBootstrapService(UserRepository userRepository,
                                 PasswordEncoder passwordEncoder,
                                 AdminProperties adminProperties,
                                 Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminProperties = adminProperties;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedIfMissing();
    }

    /** Seeds the configured ADMIN account when none exists. */
    @Transactional
    public void seedIfMissing() {
        if (userRepository.existsByRole(Role.ADMIN)) {
            return;
        }
        if (adminProperties.username() == null || adminProperties.password() == null) {
            log.warn("No ADMIN exists and app.admin.username/password are not configured; skipping seed");
            return;
        }

        String email = adminProperties.username() + "@local.admin";
        User admin = new User(
                adminProperties.username(),
                email,
                passwordEncoder.encode(adminProperties.password()),
                Role.ADMIN,
                true,
                clock.instant());
        userRepository.save(admin);
        log.info("Seeded bootstrap ADMIN account username={}", admin.getUsername());
    }
}
