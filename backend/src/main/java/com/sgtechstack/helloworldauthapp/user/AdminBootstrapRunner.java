package com.sgtechstack.helloworldauthapp.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a single initial {@code ADMIN} account on startup, from
 * {@code app.admin.username} / {@code app.admin.password}, hashed exactly
 * like any other account's password. Runs once per startup; if an
 * {@code ADMIN} already exists (from a previous run, or created some
 * other way), does nothing — this is a bootstrap for the very first
 * deployment, not an upsert.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;

    public AdminBootstrapRunner(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.username}") String adminUsername,
            @Value("${app.admin.password}") String adminPassword
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(Role.ADMIN)) {
            log.info("Admin bootstrap skipped: an ADMIN account already exists");
            return;
        }

        String passwordHash = passwordEncoder.encode(adminPassword);
        String placeholderEmail = adminUsername + "@admin.local";

        User admin = new User(adminUsername, placeholderEmail, passwordHash, Role.ADMIN, true);
        userRepository.save(admin);

        log.info("Seeded initial admin account username={}", adminUsername);
    }
}
