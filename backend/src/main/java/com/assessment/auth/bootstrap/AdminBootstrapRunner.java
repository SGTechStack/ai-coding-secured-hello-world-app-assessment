package com.assessment.auth.bootstrap;

import com.assessment.auth.audit.AuditAction;
import com.assessment.auth.audit.AuditEvent;
import com.assessment.auth.audit.AuditLogger;
import com.assessment.auth.audit.AuditReason;
import com.assessment.auth.common.ApiException;
import com.assessment.auth.password.PasswordPolicy;
import com.assessment.auth.user.AccountCreationService;
import com.assessment.auth.user.DeletedUserRepository;
import com.assessment.auth.user.Role;
import com.assessment.auth.user.User;
import com.assessment.auth.user.UserRepository;
import org.slf4j.event.Level;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Seeds the initial administrator on first start (story 1.21, spec.md S9).
 *
 * <p><strong>An {@code ApplicationRunner}, not a Liquibase changeset</strong>, and
 * profile-independent. {@code Common_Automatic…:276} explicitly blesses startup runners for
 * bootstrap user seeding, and a changeset cannot call {@code PasswordEncoder} — the hash would have
 * to be a literal in version control, twice over counting the history row.
 *
 * <p><strong>The existence check is "does any user hold USER_MANAGER, in any state".</strong> Not
 * the recipe's {@code count == 0}: roles are seeded on every boot and self-registration makes the
 * user count non-zero. And not "any <em>enabled</em>" either, because a disabled sole administrator
 * would let this runner try to seed a username that already exists.
 *
 * <p>Startup <strong>fails</strong> rather than degrading, in three cases: no password, a password
 * that violates the policy, and a configured username that is already tombstoned. The third is
 * worth stating plainly — a tombstoned administrator username can never be re-seeded, and failing
 * loudly is the only honest response.
 */
@Component
@Order(AdminBootstrapRunner.ORDER)
public class AdminBootstrapRunner implements ApplicationRunner {

  static final int ORDER = RoleSeedRunner.ORDER + 100;

  private final UserRepository userRepository;
  private final DeletedUserRepository deletedUserRepository;
  private final AccountCreationService accountCreationService;
  private final PasswordPolicy passwordPolicy;
  private final AuditLogger auditLogger;
  private final AdminProperties properties;

  public AdminBootstrapRunner(
      UserRepository userRepository,
      DeletedUserRepository deletedUserRepository,
      AccountCreationService accountCreationService,
      PasswordPolicy passwordPolicy,
      AuditLogger auditLogger,
      AdminProperties properties) {
    this.userRepository = userRepository;
    this.deletedUserRepository = deletedUserRepository;
    this.accountCreationService = accountCreationService;
    this.passwordPolicy = passwordPolicy;
    this.auditLogger = auditLogger;
    this.properties = properties;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (userRepository.existsByRole(Role.USER_MANAGER)) {
      // A restart that seeds nothing emits no audit event (story 1.21).
      return;
    }

    String username = properties.username();
    String password = properties.password();
    String email = properties.email();

    if (password == null || password.isBlank()) {
      throw new IllegalStateException(
          "APP_ADMIN_PASSWORD is not set. Refusing to start: seeding a default or guessable "
              + "administrator credential is never an acceptable fallback.");
    }

    if (deletedUserRepository.existsByUsername(username)) {
      throw new IllegalStateException(
          "The configured administrator username '"
              + username
              + "' exists in the tombstone table and can never be reused. Configure a different "
              + "APP_ADMIN_USERNAME. Refusing to start rather than skipping the seed silently.");
    }

    // Validated BEFORE any write, so a weak password never produces a half-seeded account.
    try {
      passwordPolicy.validate(password, username, email);
    } catch (ApiException ex) {
      throw new IllegalStateException(
          "APP_ADMIN_PASSWORD violates the password policy: " + ex.getMessage(), ex);
    }

    User admin =
        accountCreationService.create(username, email, password, Role.USER_MANAGER, true);

    auditLogger.emit(
        AuditEvent.of(AuditAction.SYSTEM, AuditReason.ADMIN_BOOTSTRAP_SEEDED, Level.INFO)
            // A literal system actor, not an omission (ticket 12's amendment to ticket 16).
            .target(admin.getId())
            .build());
  }
}
