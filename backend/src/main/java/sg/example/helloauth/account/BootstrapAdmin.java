package sg.example.helloauth.account;

import java.util.List;

import jakarta.validation.Validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import sg.example.helloauth.DevProfile;
import sg.example.helloauth.api.ApiException;
import sg.example.helloauth.audit.AuditLogger;
import sg.example.helloauth.password.PasswordPolicy;

/**
 * The operator's way into a fresh deployment (ADR-0008). At startup, in every profile, it creates
 * the Bootstrap admin from {@code app.admin.*}, but only if no active Admin exists, so a restart
 * never adds a second. Outside the dev profile the settings are required, so there are never
 * default admin credentials. The password must pass the same Password policy as any other.
 */
@Component
class BootstrapAdmin implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdmin.class);

    private final BootstrapAdminProperties settings;
    private final AccountService accounts;
    private final PasswordPolicy passwordPolicy;
    private final AuditLogger audit;
    private final Validator validator;
    private final boolean devProfile;

    BootstrapAdmin(BootstrapAdminProperties settings, AccountService accounts, PasswordPolicy passwordPolicy,
            AuditLogger audit, Validator validator, Environment environment) {
        this.settings = settings;
        this.accounts = accounts;
        this.passwordPolicy = passwordPolicy;
        this.audit = audit;
        this.validator = validator;
        this.devProfile = DevProfile.isActive(environment);
    }

    @Override
    public void run(ApplicationArguments args) {
        createIfNoActiveAdmin();
    }

    /** @throws IllegalStateException when the settings are missing or unusable, failing startup */
    void createIfNoActiveAdmin() {
        if (!settings.isComplete()) {
            if (devProfile) {
                log.info("No Bootstrap admin configured");
                return;
            }
            throw new IllegalStateException("app.admin.username, app.admin.password and app.admin.email must all be set");
        }
        if (accounts.hasActiveAdmin()) {
            return;
        }
        requireRegistrationRule("username", settings.username());
        requireRegistrationRule("email", settings.email());
        List<String> violations = passwordPolicy.violations(settings.password());
        if (!violations.isEmpty()) {
            throw new IllegalStateException("app.admin.password " + String.join(", ", violations));
        }
        Account admin;
        try {
            admin = accounts.createAdmin(settings.username(), settings.email(), settings.password());
        } catch (ApiException ex) {
            throw new IllegalStateException("app.admin.username or app.admin.email belongs to another Account, "
                    + "or to a Tombstone");
        }
        audit.bootstrapAdminCreated(admin.getId());
    }

    /** Holds a setting to the rule registration applies to the same field. */
    private void requireRegistrationRule(String field, String value) {
        validator.validateValue(RegistrationController.RegistrationRequest.class, field, value).stream()
                .findFirst()
                .ifPresent(violation -> {
                    throw new IllegalStateException("app.admin." + field + " " + violation.getMessage());
                });
    }
}
