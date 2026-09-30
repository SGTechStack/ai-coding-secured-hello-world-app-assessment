package org.eds.demo.user.application;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eds.demo.common.exception.ConflictException;
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

  private final AppUserRepository appUserRepository;
  private final PasswordEncoder passwordEncoder;
  private final TemporaryPasswordGenerator temporaryPasswordGenerator;
  private final TemporaryPasswordProperties temporaryPasswordProperties;
  private final Clock clock;

  @Transactional
  public CreatedAccount createAccount(String actor, String username, Role role) {
    if (appUserRepository.existsByUsername(username)) {
      throw usernameTaken();
    }
    var temporaryPassword = temporaryPasswordGenerator.generate();
    var expiresAt = clock.instant().plus(temporaryPasswordProperties.ttl());
    var account = AppUser.create(username, Set.of(role));
    account.issueTemporaryPassword(passwordEncoder.encode(temporaryPassword), expiresAt);
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
    return new CreatedAccount(username, role, temporaryPassword, expiresAt);
  }

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
