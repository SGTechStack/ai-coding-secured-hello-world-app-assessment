package com.eitri.admin;

import com.eitri.auth.AccountConflictException;
import com.eitri.auth.AccountService;
import com.eitri.auth.AccountView;
import com.eitri.auth.PasswordPolicy;
import com.eitri.auth.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Creates the initial admin account from {@code app.admin.*} on a database with no {@code ADMIN}, so an
 * operator can reach the admin module without editing the database. Once any admin exists it does
 * nothing, so restarts never duplicate the admin or overwrite a password changed since. Startup fails
 * when there is no admin and the credentials are missing or the password is weak; errors never contain
 * the configured password.
 */
@Component
@EnableConfigurationProperties(AdminBootstrapProperties.class)
class AdminBootstrap implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AccountService accounts;
    private final AdminBootstrapProperties properties;

    AdminBootstrap(AccountService accounts, AdminBootstrapProperties properties) {
        this.accounts = accounts;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (accounts.adminExists()) {
            return;
        }
        String username = required(properties.username(), "app.admin.username");
        String email = required(properties.email(), "app.admin.email");
        String password = required(properties.password(), "app.admin.password");
        if (PasswordPolicy.violation(password).isPresent()) {
            throw new IllegalStateException("The configured admin password does not meet the password policy");
        }

        AccountView admin;
        try {
            admin = accounts.create(username, email, password, Role.ADMIN);
        } catch (AccountConflictException conflict) {
            String property = conflict.field() == AccountConflictException.Field.USERNAME
                    ? "app.admin.username"
                    : "app.admin.email";
            throw new IllegalStateException(property + " is already used by a non-admin account");
        }
        LOGGER.atInfo()
                .addKeyValue("user.id", admin.id().toString())
                .setMessage("Initial admin account created")
                .log();
    }

    private static String required(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(property + " must be configured: no admin account exists yet");
        }
        return value;
    }
}
