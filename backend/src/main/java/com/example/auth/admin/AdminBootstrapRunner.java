package com.example.auth.admin;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.example.auth.config.AdminProperties;
import com.example.auth.user.PasswordPolicy;
import com.example.auth.user.Role;
import com.example.auth.user.User;
import com.example.auth.user.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Seeds an initial ADMIN account on startup (Story 12) — idempotently.
 *
 * Story 51 hardening: there is no usable default admin password.
 * - The password must be configured and meet the same length policy as
 *   registration (12–72). A blank or too-short/long value fails startup.
 * - Under any non-dev profile, a KNOWN PLACEHOLDER password (e.g. the one shipped
 *   in application.yml) is rejected and startup fails fast, forcing operators to
 *   supply a real secret. In dev/local the placeholder is accepted for convenience.
 *
 * The plaintext password is never logged; only its BCrypt hash is persisted.
 */
@Component
@ConditionalOnProperty(prefix = "app.admin", name = "bootstrap-enabled", havingValue = "true", matchIfMissing = true)
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private static final int MIN_PASSWORD_LENGTH = 12;
    private static final int MAX_PASSWORD_LENGTH = 72; // BCrypt byte cap
    private static final Set<String> PLACEHOLDER_PASSWORDS = Set.of(
            "change-me-please-12+", "changeme", "changeme12345", "password", "password1234",
            "admin", "administrator", "admin1234567");

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final AdminProperties props;
    private final Environment environment;

    public AdminBootstrapRunner(UserRepository users,
                                PasswordEncoder passwordEncoder,
                                Clock clock,
                                AdminProperties props,
                                Environment environment) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.props = props;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.existsByRole(Role.ADMIN)) {
            log.info("event=admin_bootstrap_skipped reason=admin_exists");
            return;
        }

        validatePassword(props.password());

        User admin = new User(
                props.username(),
                props.email(),
                passwordEncoder.encode(props.password()),
                Role.ADMIN,
                clock.instant());
        users.save(admin);
        log.info("event=admin_bootstrap_seeded username={}", props.username());
    }

    private void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "app.admin.password must be configured to bootstrap the initial admin account");
        }
        if (password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "app.admin.password must be between 12 and 72 characters");
        }
        if (!isDevMode() && PLACEHOLDER_PASSWORDS.contains(password)) {
            throw new IllegalStateException(
                    "Refusing to seed the initial admin with a known placeholder password under a non-dev "
                    + "profile. Set a strong app.admin.password (e.g. via the APP_ADMIN_PASSWORD env var).");
        }
        if (!isDevMode()) {
            List<String> violations = PasswordPolicy.validate(password);
            if (!violations.isEmpty()) {
                throw new IllegalStateException(
                        "app.admin.password does not meet the password policy: " + violations);
            }
        }
    }

    /** Local/default (no active profile) or an explicit "dev" profile counts as dev. */
    private boolean isDevMode() {
        String[] active = environment.getActiveProfiles();
        return active.length == 0 || Arrays.asList(active).contains("dev");
    }
}
