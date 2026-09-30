package local.builderday.account.adminbootstrap.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import local.builderday.account.adminbootstrap.config.AdminBootstrapProperties;
import local.builderday.account.core.model.AccountRuleViolation;
import local.builderday.account.core.model.Role;
import local.builderday.account.core.repository.UserRepository;
import local.builderday.account.core.service.AccountRules;
import local.builderday.account.core.service.PasswordPolicy;
import local.builderday.account.core.service.UserProfileService;
import local.builderday.common.audit.SecurityAudit;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Admin bootstrap (Story 12, ADR 0009): at startup, creates the first Admin from {@code ADMIN_USERNAME} /
 * {@code ADMIN_PASSWORD} only if no account with the {@code ADMIN} role has ever existed. A disabled or tombstoned
 * Admin still counts, so it is never a recovery or promotion path.
 *
 * <p>The check and the insert run under the ShedLock lock {@code admin-bootstrap}, so several instances starting
 * together create exactly one Admin. Startup fails on misconfiguration (one variable set, a rule violated, the
 * username taken, or the insert failing); an absent pair only warns. A
 * creation and a failed attempt are Security audit events; a skip is only an application log line.
 */
@Service
public class AdminBootstrap {
  static final String ADMIN_ROLE = Role.ADMIN.name();
  static final String LOCK_NAME = "admin-bootstrap";
  private static final Duration LOCK_AT_MOST_FOR = Duration.ofMinutes(1);
  private static final Duration LOCK_AT_LEAST_FOR = Duration.ZERO;
  private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

  private final AdminBootstrapProperties properties;
  private final UserRepository userRepository;
  private final UserProfileService userProfileService;
  private final PasswordPolicy passwordPolicy;
  private final PasswordEncoder passwordEncoder;
  private final LockProvider lockProvider;

  AdminBootstrap(AdminBootstrapProperties properties, UserRepository userRepository,
      UserProfileService userProfileService, PasswordPolicy passwordPolicy, PasswordEncoder passwordEncoder,
      LockProvider lockProvider) {
    this.properties = properties;
    this.userRepository = userRepository;
    this.userProfileService = userProfileService;
    this.passwordPolicy = passwordPolicy;
    this.passwordEncoder = passwordEncoder;
    this.lockProvider = lockProvider;
  }

  /**
   * Runs Admin bootstrap once, under the lock. Safe to call again: a second run finds the Admin and creates nothing.
   *
   * @throws AdminBootstrapException when the configuration cannot safely produce an Admin, to stop startup
   */
  public void bootstrap() {
    Optional<SimpleLock> lock = lockProvider.lock(
        new LockConfiguration(Instant.now(), LOCK_NAME, LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR));
    if (lock.isEmpty()) {
      log.atInfo().log("Admin bootstrap skipped: another instance holds the {} lock.", LOCK_NAME);
      return;
    }
    try {
      seedIfAbsent();
    } finally {
      lock.get().unlock();
    }
  }

  private void seedIfAbsent() {
    if (userRepository.existsByRole(ADMIN_ROLE)) {
      if (properties.anySet()) {
        log.atWarn().log("An Admin already exists; ignoring the configured admin credentials. "
            + "Remove ADMIN_USERNAME and ADMIN_PASSWORD from the environment.");
      } else {
        log.atDebug().log("An Admin already exists; nothing to bootstrap.");
      }
      return;
    }
    if (!properties.anySet()) {
      log.atWarn().log("No Admin exists and no admin credentials are configured; starting without an Admin. "
          + "Set ADMIN_USERNAME and ADMIN_PASSWORD to bootstrap one.");
      return;
    }
    if (properties.exactlyOneSet()) {
      // Nothing was attempted, so no audit event (ADR 0009 §4/§6).
      throw AdminBootstrapException.of("misconfigured",
          "set both ADMIN_USERNAME and ADMIN_PASSWORD, or neither.");
    }
    create();
  }

  private void create() {
    String username = AccountRules.normalize(properties.username());
    List<AccountRuleViolation> usernameViolations = AccountRules.usernameViolations(username);
    if (!usernameViolations.isEmpty()) {
      auditFailure("username-invalid");
      throw AdminBootstrapException.ofViolations("username-invalid", usernameViolations);
    }
    List<AccountRuleViolation> passwordViolations = passwordPolicy.violations(properties.password(), username, null);
    if (!passwordViolations.isEmpty()) {
      auditFailure("password-policy");
      throw AdminBootstrapException.ofViolations("password-policy", passwordViolations);
    }
    Optional<UUID> created;
    try {
      created = userProfileService.createAccount(username, null, passwordEncoder.encode(properties.password()),
          ADMIN_ROLE);
    } catch (RuntimeException insertFailed) {
      auditFailure("error");
      throw AdminBootstrapException.of("error", "the Admin could not be stored.");
    }
    if (created.isEmpty()) {
      auditFailure("username-taken");
      throw AdminBootstrapException.of("username-taken", "the configured username already belongs to an account.");
    }
    auditSuccess(created.get());
    log.atInfo().addKeyValue("user.id", created.get().toString()).log("Admin bootstrap created the first Admin.");
  }

  private void auditSuccess(UUID accountId) {
    SecurityAudit.recordFrom(null, new SecurityAudit.Event(
        "account-created", "iam", "creation", SecurityAudit.Outcome.SUCCESS, "admin-bootstrap", accountId));
  }

  private void auditFailure(String reason) {
    SecurityAudit.recordFrom(null, new SecurityAudit.Event(
        "account-created", "iam", "creation", SecurityAudit.Outcome.FAILURE, reason, null));
  }
}
