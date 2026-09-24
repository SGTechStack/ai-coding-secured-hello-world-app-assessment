package com.example.helloauth.config;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Prod-profile fail-fast for the admin seed credentials (ticket 14). The
 * prod profile must refuse to boot without
 * {@code APP_ADMIN_USERNAME}/{@code APP_ADMIN_PASSWORD} — otherwise it would
 * start a system with no way into {@code /api/admin/**} (the seeder stands
 * down on blank creds) or, worse, seed an admin whose username is a literal
 * unresolved {@code ${…}} placeholder.
 *
 * <p>Enforced here rather than by placeholder binding: the Boot binder
 * tolerates an unresolvable {@code ${APP_ADMIN_*}} by binding the literal
 * text, so "no default" does not fail fast on its own. A constructor throw
 * fails bean creation during context refresh — before the
 * {@link com.example.helloauth.admin.AdminSeeder} runner — and
 * {@code SpringApplication.run} exits non-zero.
 */
@Component
@Profile("prod")
public class ProdAdminCredentialsValidator {

    public ProdAdminCredentialsValidator(AppProperties properties) {
        AppProperties.Admin admin = properties.getAdmin();
        if (isBlank(admin.getUsername()) || isBlank(admin.getPassword())) {
            throw new IllegalStateException(
                "The prod profile requires APP_ADMIN_USERNAME and "
                    + "APP_ADMIN_PASSWORD environment variables — without "
                    + "them no admin account can be seeded and /api/admin/** "
                    + "stays unreachable.");
        }
        // Presence isn't enough: AdminSeeder treats a sub-minimum password
        // as warn-only, which suits dev but would let a prod admin ship
        // below the registered-account floor. Prod fails fast instead.
        if (admin.getPassword().length() < properties.getPasswordMinLength()) {
            throw new IllegalStateException(
                "APP_ADMIN_PASSWORD is shorter than app.password-min-length ("
                    + properties.getPasswordMinLength() + " characters).");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
