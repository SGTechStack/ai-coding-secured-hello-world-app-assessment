package com.example.demo_app.admin;

import com.example.demo_app.audit.AuditEvent;
import com.example.demo_app.audit.AuditLog;
import com.example.demo_app.user.AccountRegistration;
import com.example.demo_app.user.NewAccount;
import com.example.demo_app.user.PasswordPolicy;
import com.example.demo_app.user.Role;
import com.example.demo_app.user.UserAccount;
import com.example.demo_app.user.UserAccountRepository;
import com.example.demo_app.web.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Makes sure an admin exists once the application has started, so an operator can reach the admin
 * module without editing the database. Runners start after the context is refreshed, so Flyway has
 * already migrated the schema.
 *
 * <ul>
 *   <li>An existing {@code ADMIN} (any one) means there is nothing to do, whatever {@code
 *       app.admin.*} says: a restart never creates a second admin.
 *   <li>Otherwise it creates one from {@link AdminBootstrapProperties} through {@link
 *       AccountRegistration}, so the admin gets the same normalisation, password policy and
 *       encoder as every other account, and audits {@code ADMIN_BOOTSTRAPPED}.
 *   <li>If that isn't possible (a property missing, a password the policy refuses, a username or
 *       email already taken), startup fails: an app nobody can administer must not run. The
 *       failure message names the property and the problem, never the password.
 * </ul>
 */
@Component
@EnableConfigurationProperties(AdminBootstrapProperties.class)
class AdminBootstrap implements ApplicationRunner {

  /** The bootstrap admin's first name: {@code app.admin} has only the three required values. */
  static final String FIRST_NAME = "Admin";

  private final AdminBootstrapProperties properties;
  private final UserAccountRepository accounts;
  private final AccountRegistration registration;
  private final PasswordPolicy passwordPolicy;
  private final AuditLog auditLog;

  AdminBootstrap(
      AdminBootstrapProperties properties,
      UserAccountRepository accounts,
      AccountRegistration registration,
      PasswordPolicy passwordPolicy,
      AuditLog auditLog) {
    this.properties = properties;
    this.accounts = accounts;
    this.registration = registration;
    this.passwordPolicy = passwordPolicy;
    this.auditLog = auditLog;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (accounts.existsByRole(Role.ADMIN)) {
      return;
    }
    requirePropertiesSet();
    passwordPolicy
        .problem(properties.password())
        .ifPresent(
            problem -> {
              throw failure("app.admin.password does not meet the password policy: " + problem);
            });

    UserAccount admin;
    try {
      admin =
          registration.register(
              new NewAccount(
                  properties.username(),
                  properties.email(),
                  FIRST_NAME,
                  properties.password()),
              Role.ADMIN);
    } catch (ApiException refused) {
      // ACCOUNT_CONFLICT: a non-admin account already holds the username or email.
      throw failure(
          refused.getMessage()
              + " (app.admin.username or app.admin.email is already used by another account)");
    }
    auditLog.recordSystem(AuditEvent.ADMIN_BOOTSTRAPPED, Map.of("target", admin.getUsername()));
  }

  private void requirePropertiesSet() {
    List<String> missing = new ArrayList<>();
    if (isBlank(properties.username())) {
      missing.add("app.admin.username (APP_ADMIN_USERNAME)");
    }
    if (isBlank(properties.email())) {
      missing.add("app.admin.email (APP_ADMIN_EMAIL)");
    }
    if (isBlank(properties.password())) {
      missing.add("app.admin.password (APP_ADMIN_PASSWORD)");
    }
    if (!missing.isEmpty()) {
      throw failure("set " + String.join(", ", missing));
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static IllegalStateException failure(String detail) {
    return new IllegalStateException(
        "No ADMIN account exists and the bootstrap admin cannot be created: " + detail);
  }
}
