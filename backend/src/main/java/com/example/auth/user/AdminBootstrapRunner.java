package com.example.auth.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Ensures at least one {@code ADMIN} account exists on startup, replacing the
 * old {@code data.sql} seed now that registration exists to create ordinary
 * users. Runs on every startup but is a no-op once an admin already exists,
 * so restarts never create duplicates.
 */
@Component
@Order(1)
public class AdminBootstrapRunner implements CommandLineRunner {

    private static final int MINIMUM_PASSWORD_LENGTH = 12;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;
    private final String adminEmail;

    public AdminBootstrapRunner(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.username}") String adminUsername,
            @Value("${app.admin.password}") String adminPassword,
            @Value("${app.admin.email}") String adminEmail) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.adminEmail = adminEmail;
    }

    @Override
    public void run(String... args) {
        if (userRepository.existsByRole(Role.ADMIN)) {
            return;
        }
        // Fail fast rather than seed a weak admin credential: this must be
        // caught at startup, not discovered later as a live vulnerability.
        if (adminPassword == null || adminPassword.length() < MINIMUM_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "app.admin.password must be at least " + MINIMUM_PASSWORD_LENGTH + " characters long");
        }
        User admin = new User(adminUsername, adminEmail, passwordEncoder.encode(adminPassword), "Admin");
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);
    }
}
