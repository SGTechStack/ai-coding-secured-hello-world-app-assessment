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
import java.util.Optional;

/**
 * Seeds a single initial {@code ADMIN} account on startup, from
 * {@code app.admin.username} / {@code app.admin.password}, hashed exactly
 * like any other account's password.
 *
 * <h2>The condition is "no admin who can sign in", not "no admin row"</h2>
 *
 * This used to skip on {@code existsByRole(ADMIN)}. A disabled {@code ADMIN}
 * row satisfies that check while being unable to authenticate, so a system whose
 * only administrator had been disabled looked bootstrapped and was in fact
 * locked out of its own administration — recoverable only by editing the
 * database directly, which is the situation the bootstrap story exists to
 * prevent. The condition is now {@code existsByRoleAndEnabledTrue}, so a
 * restart recovers that state.
 *
 * <p>Pair this with {@code LastAdminGuard}, which stops the state arising
 * through the application in the first place. The two are complementary: the
 * guard closes the door, this reopens it if the door was shut some other way (a
 * manual database edit, a restore from a partial backup, a bug in a future
 * mutation path that forgot the guard).
 *
 * <h2>Reviving versus creating</h2>
 *
 * With the condition relaxed, seeding can now run while an account already holds
 * the configured username — a disabled admin, typically the same one. Inserting
 * would violate the unique constraint, so that row is revived instead: enabled,
 * password reset, lockout state cleared.
 *
 * <p>Reviving is confined to rows that are <em>already</em> {@code ADMIN}. If
 * the configured username belongs to a regular account, startup logs an error
 * and seeds nothing. Promoting it would be a privilege escalation reachable by
 * setting one environment variable, bypassing {@code RoleMutationGuard} and
 * leaving no audit record — a worse outcome than refusing to recover.
 *
 * <h2>The generated password</h2>
 *
 * If the configured password is blank, a random one is generated for that boot
 * and logged once. This exists so local development works with no setup without
 * shipping a fixed, publicly-known admin password — the dev profile previously
 * defaulted to {@code password1234}, which was also printed in the README and
 * rendered in the login form, making any reachable dev instance a one-guess
 * admin takeover.
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
        if (userRepository.existsByRoleAndEnabledTrue(Role.ADMIN)) {
            log.info("Admin bootstrap skipped: an enabled ADMIN account already exists");
            return;
        }

        boolean generated = adminPassword == null || adminPassword.isBlank();
        String password = generated ? generateRandomPassword() : adminPassword;
        String passwordHash = passwordEncoder.encode(password);

        Optional<User> existing = userRepository.findByUsernameIgnoreCase(adminUsername);

        if (existing.isPresent()) {
            User account = existing.get();

            if (account.getRole() != Role.ADMIN) {
                log.error("""
                        Cannot bootstrap an admin account: no enabled ADMIN exists, but the configured \
                        app.admin.username is already held by a non-admin account. Refusing to promote it \
                        — that would grant admin rights from a single environment variable, with no audit \
                        record. Choose a different app.admin.username, or promote an account through the \
                        admin API.""");
                return;
            }

            // A disabled admin, revived rather than duplicated. The lockout
            // state is cleared too: an account disabled after a run of failed
            // logins would otherwise come back still locked, which looks
            // identical to the bootstrap not having worked.
            account.setEnabled(true);
            account.setPasswordHash(passwordHash);
            account.setFailedLoginAttempts(0);
            account.setLockedUntil(null);
            account.setLastFailedLoginAt(null);
            userRepository.save(account);

            log.warn("Re-enabled the existing disabled ADMIN account username={} passwordSource={} "
                            + "— no enabled admin remained",
                    adminUsername, generated ? "generated" : "configured");
        } else {
            String placeholderEmail = adminUsername + "@admin.local";
            userRepository.save(new User(adminUsername, placeholderEmail, passwordHash, Role.ADMIN, true));

            log.info("Seeded initial admin account username={} passwordSource={}",
                    adminUsername, generated ? "generated" : "configured");
        }

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
