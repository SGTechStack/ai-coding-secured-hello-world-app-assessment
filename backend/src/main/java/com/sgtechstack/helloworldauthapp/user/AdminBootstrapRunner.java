package com.sgtechstack.helloworldauthapp.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Seeds a single initial {@code ADMIN} account on startup, from
 * {@code app.admin.username} / {@code app.admin.password}, hashed exactly
 * like any other account's password. Runs once per startup; if an
 * {@code ADMIN} already exists (from a previous run, or created some
 * other way), does nothing — this is a bootstrap for the very first
 * deployment, not an upsert.
 *
 * <p>If the configured password is blank, a random one is generated for that
 * boot and logged once. This exists so local development works with no setup
 * without shipping a fixed, publicly-known admin password — the dev profile
 * previously defaulted to {@code password1234}, which was also printed in the
 * README and rendered in the login form, making any reachable dev instance a
 * one-guess admin takeover.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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

        boolean generated = adminPassword == null || adminPassword.isBlank();
        String password = generated ? generateRandomPassword() : adminPassword;

        String passwordHash = passwordEncoder.encode(password);
        String placeholderEmail = adminUsername + "@admin.local";

        User admin = new User(adminUsername, placeholderEmail, passwordHash, Role.ADMIN, true);
        userRepository.save(admin);

        log.info("Seeded initial admin account username={} passwordSource={}",
                adminUsername, generated ? "generated" : "configured");

        if (generated) {
            // The one credential this application prints. It has no other
            // delivery channel — nobody can use the account otherwise — and it
            // is rotated on every boot, so it is worth far less to an attacker
            // than the fixed, README-published password this replaced.
            //
            // Structurally dev-only: reaching this branch requires
            // app.admin.password to resolve to something blank, and only the
            // dev profile supplies a blank default. Outside dev the
            // placeholder has no default and startup fails instead.
            log.warn("""
                    No APP_ADMIN_PASSWORD set, so a random one was generated for this run only:

                        username: {}
                        password: {}

                    It changes on every restart. Set APP_ADMIN_PASSWORD to pin it.\
                    """, adminUsername, password);
        }
    }

    /**
     * 24 bytes of {@link SecureRandom} in URL-safe base64 — comfortably past
     * the 12-character policy minimum and not worth guessing.
     */
    private static String generateRandomPassword() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
