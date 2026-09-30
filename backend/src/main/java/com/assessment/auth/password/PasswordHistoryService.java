package com.assessment.auth.password;

import com.assessment.auth.common.ApiErrorCode;
import com.assessment.auth.common.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Password reuse history (spec.md S5, ticket 04).
 *
 * <p>Blocks <strong>four values</strong>: the current password plus the three previous ones, so a
 * password becomes available again only after four subsequent changes. The newest history row
 * <em>is</em> the current hash, which is what makes that a single ordered query.
 *
 * <p>⚠️ Self-Service:269-272's verification procedure assumes a different depth and is wrong for
 * this application. It must not be transcribed.
 */
@Service
public class PasswordHistoryService {

  private final PasswordHistoryRepository repository;
  private final PasswordEncoder passwordEncoder;
  private final PasswordProperties properties;
  private final Clock clock;

  public PasswordHistoryService(
      PasswordHistoryRepository repository,
      PasswordEncoder passwordEncoder,
      PasswordProperties properties,
      Clock clock) {
    this.repository = repository;
    this.passwordEncoder = passwordEncoder;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * Rejects a candidate that matches any of the retained hashes.
   *
   * <p>This is an O(depth) BCrypt comparison, which is deliberate: the hashes are salted, so there
   * is no way to look one up by value.
   */
  public void assertNotReused(UUID userId, String rawPassword) {
    List<PasswordHistory> recent =
        repository.findByUserIdOrderByCreatedAtDesc(userId, Limit.of(properties.historyDepth()));
    for (PasswordHistory entry : recent) {
      if (passwordEncoder.matches(rawPassword, entry.getPasswordHash())) {
        throw new ApiException(
            ApiErrorCode.VALIDATION_FAILED,
            "Password must not repeat any of your last "
                + properties.historyDepth()
                + " passwords.");
      }
    }
  }

  /** Records the hash that has just become current. Called by all four write paths. */
  public void record(UUID userId, String passwordHash) {
    repository.save(
        new PasswordHistory(UUID.randomUUID(), userId, passwordHash, clock.instant()));
  }

  /**
   * When the account's current credential was written. Derived from the newest history row rather
   * than stored on {@code users} — the row already exists and a second column would be a second
   * source of truth.
   */
  public Optional<Instant> lastChangedAt(UUID userId) {
    return repository.findByUserIdOrderByCreatedAtDesc(userId, Limit.of(1)).stream()
        .findFirst()
        .map(PasswordHistory::getCreatedAt);
  }
}
