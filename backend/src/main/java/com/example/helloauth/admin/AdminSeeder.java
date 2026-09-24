package com.example.helloauth.admin;

import com.example.helloauth.config.AppProperties;
import com.example.helloauth.user.Role;
import com.example.helloauth.user.User;
import com.example.helloauth.user.UserRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Initial-admin bootstrap (ticket 13): on startup, if no {@code ADMIN}
 * account exists, seed one from {@code app.admin.*} configuration —
 * env-var backed ({@code APP_ADMIN_USERNAME}/{@code APP_ADMIN_PASSWORD}/
 * {@code APP_ADMIN_EMAIL}), BCrypt-hashed exactly like a registered
 * account. Idempotent: {@code existsByRole(ADMIN)} makes restarts a no-op,
 * so the seeded row is never duplicated.
 *
 * <p>Blank credentials mean "not configured" — the seeder logs and stands
 * down (test and local contexts never carry them). The prod profile
 * (ticket 14) makes the env vars required via
 * {@link com.example.helloauth.config.ProdAdminCredentialsValidator}, so a
 * missing config fails fast at startup rather than silently yielding a
 * system with no way into the admin module.
 */
@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final AppProperties properties;

    public AdminSeeder(UserRepository users, PasswordEncoder passwordEncoder,
            Clock clock, AppProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedAdminIfAbsent();
    }

    /** Seeds the configured admin unless an ADMIN row already exists. */
    public void seedAdminIfAbsent() {
        if (users.existsByRole(Role.ADMIN)) {
            return;
        }
        AppProperties.Admin admin = properties.getAdmin();
        String username = admin.getUsername();
        String password = admin.getPassword();
        if (isBlank(username) || isBlank(password)) {
            log.warn("No ADMIN account exists and app.admin.username/"
                + "app.admin.password are unset — skipping initial-admin "
                + "seed. The admin module stays unreachable until an admin "
                + "account exists.");
            return;
        }
        if (users.existsByUsername(username)) {
            // A USER row already holds the configured name — an INSERT
            // would die on the unique constraint; surface it as a clear
            // operator-facing message instead.
            log.warn("app.admin.username '{}' is already taken by a "
                + "non-admin account — skipping initial-admin seed.",
                username);
            return;
        }
        String email = admin.getEmail();
        if (users.existsByEmail(email)) {
            // users.email is unique + non-null too — a registered
            // non-admin row holding the configured address would crash
            // the INSERT (and startup) the same way.
            log.warn("app.admin.email '{}' is already taken by a "
                + "non-admin account — skipping initial-admin seed.",
                email);
            return;
        }
        int minLength = properties.getPasswordMinLength();
        if (password.length() < minLength) {
            // Seed anyway — the registration-time min-length rule doesn't
            // bind the config-driven seed; flag the weak credential for
            // the operator (fail-fast hardening is ticket 14's).
            log.warn("app.admin.password is shorter than "
                + "app.password-min-length ({} < {}) — seeding anyway; "
                + "use a longer APP_ADMIN_PASSWORD.",
                password.length(), minLength);
        }

        User seeded = new User();
        seeded.setUsername(username);
        seeded.setEmail(email);
        seeded.setPasswordHash(passwordEncoder.encode(password));
        seeded.setRole(Role.ADMIN);
        seeded.setEnabled(true);
        seeded.setCreatedAt(clock.instant());
        users.save(seeded);
        log.info("Seeded initial admin account '{}'.", username);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
