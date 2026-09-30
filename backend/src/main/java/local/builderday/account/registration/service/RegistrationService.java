package local.builderday.account.registration.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import local.builderday.notification.service.EmailService;
import local.builderday.account.registration.model.RegistrationViolation;
import local.builderday.account.registration.service.dto.RegistrationInput;
import local.builderday.account.core.model.AccountRuleViolation;
import local.builderday.account.core.service.AccountRules;
import local.builderday.account.core.service.PasswordPolicy;
import local.builderday.account.core.service.UserProfileService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Registration policy: source lockout, validation, rejection counting, and creation of enabled least-privilege
 * accounts. Never authenticates the Visitor.
 *
 * <p>The Registration lockout ({@link RegistrationLockout#attempt}) owns ordering and counting; this supplies only
 * screening (validation, hashing) and the decision (account creation). The account insert's unique constraints remain
 * the final duplicate guard.
 */
@Service
public class RegistrationService {
  private final UserProfileService userProfileService;
  private final PasswordEncoder passwordEncoder;
  private final PasswordPolicy passwordPolicy;
  private final RegistrationLockout lockout;
  private final EmailService emailService;
  private final String registrationRole;

  RegistrationService(UserProfileService userProfileService, PasswordEncoder passwordEncoder,
      PasswordPolicy passwordPolicy,
      RegistrationLockout lockout, EmailService emailService,
      // Validated as a defined role at startup by security's AuthorizationProperties.
      @Value("${app.security.registration-role}") String registrationRole) {
    this.userProfileService = userProfileService;
    this.passwordEncoder = passwordEncoder;
    this.passwordPolicy = passwordPolicy;
    this.lockout = lockout;
    this.emailService = emailService;
    this.registrationRole = registrationRole;
  }

  /** @param sourceIp resolved client IP the lockout applies to */
  public RegistrationResult register(String sourceIp, RegistrationInput input) {
    return lockout.attempt(sourceIp, () -> validate(input), () -> prepare(input), this::create);
  }

  private record Account(String username, String email, String passwordHash) {}

  /** The rejection for an invalid submission, or null when it is valid. */
  private RegistrationResult.Rejected validate(RegistrationInput input) {
    var violations = input.bodyReadable() ? violations(input) : input.structuralViolations();
    return !input.bodyReadable() || !violations.isEmpty() ? new RegistrationResult.Rejected(violations) : null;
  }

  /** Hashes the password of a valid submission (slow by design, so outside the gate). */
  private Account prepare(RegistrationInput input) {
    return new Account(AccountRules.normalize(input.username()), AccountRules.normalize(input.email()),
        passwordEncoder.encode(input.password()));
  }

  /** Inside the source's gate. Empty covers both an existing account and a lost insert race. */
  private RegistrationResult create(Account account) {
    return userProfileService
        .createAccount(account.username(), account.email(), account.passwordHash(), registrationRole)
        .<RegistrationResult>map(id -> {
          notifyAfterCommit(account.email(), id);
          return new RegistrationResult.Created(id);
        })
        .orElseGet(() -> new RegistrationResult.Rejected(
            List.of(RegistrationViolation.of(RegistrationViolation.USER_EXISTS))));
  }

  private List<RegistrationViolation> violations(RegistrationInput input) {
    String username = AccountRules.normalize(input.username());
    String email = AccountRules.normalize(input.email());
    var violations = new ArrayList<>(input.structuralViolations());
    AccountRules.usernameViolations(username).forEach(code -> violations.add(violation("username", code)));
    AccountRules.emailViolations(email).forEach(code -> violations.add(violation("email", code)));
    passwordPolicy.violations(input.password(), username, email)
        .forEach(code -> violations.add(violation("password", code)));
    return violations;
  }

  /** The account rule codes are the registration wire codes of the same name. */
  private static RegistrationViolation violation(String field, AccountRuleViolation code) {
    return new RegistrationViolation(field, code.name());
  }

  /** Notifies only once the account insert has committed; a notification failure never changes the registration. */
  private void notifyAfterCommit(String email, UUID id) {
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        try {
          emailService.send(email, EmailService.NotificationType.ACCOUNT_CREATED, id);
        } catch (RuntimeException ignored) {
          // Best effort by design.
        }
      }
    });
  }
}
