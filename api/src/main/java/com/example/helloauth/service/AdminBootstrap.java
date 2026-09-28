package com.example.helloauth.service;

import com.example.helloauth.settings.AppProperties;
import com.example.helloauth.domain.Account;
import com.example.helloauth.domain.Role;
import com.example.helloauth.repository.AccountRepository;
import com.example.helloauth.service.exception.AuthExceptions.WeakPasswordException;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the first admin account at startup, so there is a way into the admin module without editing
 * the database by hand.
 *
 * <p>Idempotent by checking for the existence of <em>any</em> admin rather than for the configured
 * username specifically. That is the right test: once a human admin exists, seeding another is
 * unnecessary, and if the configured name were checked instead, renaming the seed in configuration
 * would silently mint a second admin on the next restart.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicy;
    private final AuditLog audit;
    private final AppProperties properties;
    private final Clock clock;

    public AdminBootstrap(
            AccountRepository accounts,
            PasswordEncoder passwordEncoder,
            PasswordPolicyValidator passwordPolicy,
            AuditLog audit,
            AppProperties properties,
            Clock clock) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (accounts.existsByRole(Role.ADMIN)) {
            log.debug("An admin account already exists; skipping the seed.");
            return;
        }

        AppProperties.Admin admin = properties.admin();
        if (admin.password() == null || admin.password().isBlank()) {
            log.warn(
                    "No app.admin.password is set, so no admin account was seeded. "
                            + "Set APP_ADMIN_PASSWORD and restart to create one.");
            return;
        }

        try {
            // Held to exactly the policy every other account is held to. Seeding an admin with a
            // password a visitor would be refused is not a shortcut worth having.
            passwordPolicy.validate(admin.password());
        } catch (WeakPasswordException e) {
            log.error(
                    "app.admin.password does not meet the password policy ({}), so no admin "
                            + "account was seeded.",
                    e.getMessage());
            return;
        }

        Account seeded =
                accounts.save(
                        new Account(
                                RegistrationService.normalise(admin.username()),
                                RegistrationService.normalise(admin.email()),
                                passwordEncoder.encode(admin.password()),
                                Role.ADMIN,
                                clock.instant()));

        audit.adminSeeded(seeded.getUsername());
        log.warn(
                "Seeded the initial admin account '{}' from configuration. "
                        + "Change this password before exposing the application.",
                seeded.getUsername());
    }
}
