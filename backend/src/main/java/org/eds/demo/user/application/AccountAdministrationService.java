package org.eds.demo.user.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eds.demo.common.exception.ConflictException;
import org.eds.demo.common.exception.ForbiddenException;
import org.eds.demo.common.exception.NotFoundException;
import org.eds.demo.user.domain.AppUser;
import org.eds.demo.user.domain.Role;
import org.eds.demo.user.infrastructure.AppUserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin use cases for managing Accounts. Passwords are never logged. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountAdministrationService {

  private static final String EVENT_KEY = "event";
  private static final String ACTOR_KEY = "actor";
  private static final String TARGET_KEY = "target";
  private static final String ROLE_KEY = "role";

  /** Audit event name, stable so log queries can filter on it. */
  private static final String EVENT_ACCOUNT_CREATED = "account_created";

  /** Audit event name for an admin password reset. */
  private static final String EVENT_PASSWORD_RESET = "password_reset";

  private final AppUserRepository appUserRepository;
  private final PasswordEncoder passwordEncoder;
  private final TemporaryPasswordGenerator temporaryPasswordGenerator;
  private final TemporaryPasswordProperties temporaryPasswordProperties;
  private final AccountSessions accountSessions;
  private final Clock clock;

  @Transactional
  public CreatedAccount createAccount(String actor, String username, Role role) {
    if (appUserRepository.existsByUsername(username)) {
      throw usernameTaken();
    }
    var account = AppUser.create(username, Set.of(role));
    var issued = issueTemporaryPassword(account);
    try {
      appUserRepository.saveAndFlush(account);
    } catch (DataIntegrityViolationException e) {
      // A concurrent request took the username between the check and the insert.
      throw usernameTaken();
    }
    log.atInfo()
        .addKeyValue(EVENT_KEY, EVENT_ACCOUNT_CREATED)
        .addKeyValue(ACTOR_KEY, actor)
        .addKeyValue(TARGET_KEY, username)
        .addKeyValue(ROLE_KEY, role)
        .log("Account created: actor={}, target={}, role={}", actor, username, role);
    return new CreatedAccount(
        username, role, issued.temporaryPassword(), issued.temporaryPasswordExpiresAt());
  }

  /** Issues a new Temporary Password for {@code targetId}, returned once and stored only hashed. */
  @Transactional
  public CreatedAccount resetPassword(String actor, UUID targetId) {
    var account =
        appUserRepository
            .findById(targetId)
            .orElseThrow(() -> new NotFoundException("Account", targetId));
    if (account.getUsername().equals(actor)) {
      throw new ForbiddenException("Admins cannot reset their own password");
    }
    var issued = issueTemporaryPassword(account);
    account.clearSignInBackoff();
    accountSessions.endAllSessions(account.getUsername());
    log.atInfo()
        .addKeyValue(EVENT_KEY, EVENT_PASSWORD_RESET)
        .addKeyValue(ACTOR_KEY, actor)
        .addKeyValue(TARGET_KEY, account.getUsername())
        .log("Password reset: actor={}, target={}", actor, account.getUsername());
    return new CreatedAccount(
        account.getUsername(),
        account.getRoles().stream().max(Comparator.naturalOrder()).orElseThrow(),
        issued.temporaryPassword(),
        issued.temporaryPasswordExpiresAt());
  }

  /** Generates a Temporary Password, hashes it and its expiry onto {@code account}. */
  private IssuedTemporaryPassword issueTemporaryPassword(AppUser account) {
    var temporaryPassword = temporaryPasswordGenerator.generate();
    var expiresAt = clock.instant().plus(temporaryPasswordProperties.ttl());
    account.issueTemporaryPassword(passwordEncoder.encode(temporaryPassword), expiresAt);
    return new IssuedTemporaryPassword(temporaryPassword, expiresAt);
  }

  private record IssuedTemporaryPassword(
      String temporaryPassword, Instant temporaryPasswordExpiresAt) {}

  /** Lists every Account with only the fields an admin needs; no credential state. */
  @Transactional(readOnly = true)
  public List<AccountSummary> listAccounts() {
    return appUserRepository.findAllByOrderByUsernameAsc().stream()
        .map(
            account ->
                new AccountSummary(
                    account.getId().value(),
                    account.getUsername(),
                    account.getRoles().stream().max(Comparator.naturalOrder()).orElseThrow(),
                    account.isEnabled(),
                    account.getCreatedAt()))
        .toList();
  }

  private static ConflictException usernameTaken() {
    return new ConflictException("username", "Username is already taken");
  }
}
