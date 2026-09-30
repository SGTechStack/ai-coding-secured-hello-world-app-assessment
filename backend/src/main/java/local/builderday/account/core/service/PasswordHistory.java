package local.builderday.account.core.service;

import java.time.Clock;
import java.util.UUID;
import local.builderday.account.core.config.PasswordHistoryProperties;
import local.builderday.account.core.repository.PasswordHistoryRepository;
import local.builderday.account.core.repository.UserRepository;
import local.builderday.account.core.repository.entity.PasswordHistoryEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Password history: the account's current password and the ones just before it, kept only as BCrypt hashes. */
@Component
public class PasswordHistory {
  private final PasswordHistoryRepository passwordHistoryRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final PasswordHistoryProperties properties;
  private final Clock clock;

  PasswordHistory(PasswordHistoryRepository passwordHistoryRepository, UserRepository userRepository,
      PasswordEncoder passwordEncoder, PasswordHistoryProperties properties, Clock clock) {
    this.passwordHistoryRepository = passwordHistoryRepository;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * True when {@code password} is the account's current password or one of its recent ones. The current hash is
   * always checked, which covers accounts created before the history existed. Costs up to one BCrypt per entry.
   */
  @Transactional(readOnly = true)
  public boolean contains(UUID userId, String password) {
    var current = userRepository.findById(userId).map(user -> user.getPasswordHash()).orElse(null);
    if (current != null && passwordEncoder.matches(password, current)) return true;
    return passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
        .limit(properties.historyLength())
        .anyMatch(entry -> passwordEncoder.matches(password, entry.getPasswordHash()));
  }

  /**
   * Adds the account's new current password and prunes the history to its configured length. Joins the caller's
   * transaction.
   */
  @Transactional
  void record(UUID userId, String passwordHash) {
    var current = passwordHistoryRepository.saveAndFlush(
        new PasswordHistoryEntity(UUID.randomUUID(), userId, passwordHash, clock.instant()));
    // The new entry is kept explicitly, so a timestamp tie with an older entry can never prune the current password.
    var older = passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
        .filter(entry -> !entry.getId().equals(current.getId())).toList();
    int keep = properties.historyLength() - 1;
    if (older.size() > keep) passwordHistoryRepository.deleteAllInBatch(older.subList(keep, older.size()));
  }
}
