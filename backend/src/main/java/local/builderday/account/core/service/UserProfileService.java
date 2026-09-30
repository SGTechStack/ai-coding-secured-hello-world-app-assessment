package local.builderday.account.core.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import local.builderday.account.core.config.LoginLockoutProperties;
import local.builderday.account.core.model.ResetCandidate;
import local.builderday.account.core.model.UserProfile;
import local.builderday.account.core.repository.UserRepository;
import local.builderday.account.core.repository.entity.UserEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {
  private final UserRepository userRepository;
  private final LoginLockoutProperties lockout;
  private final PasswordHistory passwordHistory;
  private final String selfServiceRole;

  /**
   * @param selfServiceRole the least-privilege role registration assigns; only accounts holding exactly it may use
   *     Password reset, so Admins, and any role added later, never recover by email alone (ADR 0004, IM8 ac-2)
   */
  public UserProfileService(UserRepository userRepository, LoginLockoutProperties lockout,
      PasswordHistory passwordHistory,
      @Value("${app.security.registration-role}") String selfServiceRole) {
    this.userRepository = userRepository;
    this.lockout = lockout;
    this.passwordHistory = passwordHistory;
    this.selfServiceRole = selfServiceRole;
  }

  @Transactional(readOnly = true)
  public Optional<UserProfile> findByUsername(String username) {
    return userRepository.findByUsername(username)
        .map(user -> new UserProfile(user.getId(), user.getUsername(), user.getRole()));
  }

  /**
   * The account's id for a Security audit event, or null when the username is unknown. Best effort: a lookup failure
   * also returns null, so auditing never changes an outcome. Deliberately not {@code @Transactional}: opening a
   * transaction could itself throw outside this guard.
   */
  public UUID findIdForAudit(String username) {
    try {
      return userRepository.findByUsername(username).map(UserEntity::getId).orElse(null);
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  /**
   * The account registered with {@code email}, with whether it may use Password reset: enabled, not deleted, not an
   * Admin (only the self-service role qualifies), and with an email. Empty when no account has that email.
   *
   * @param email normalized email
   */
  @Transactional(readOnly = true)
  public Optional<ResetCandidate> findResetCandidate(String email) {
    return userRepository.findByEmail(email).map(this::resetCandidate);
  }

  /** The same view of the account with {@code id}, e.g. to re-check eligibility when a reset completes. */
  @Transactional(readOnly = true)
  public Optional<ResetCandidate> findResetCandidate(UUID id) {
    return userRepository.findById(id).map(this::resetCandidate);
  }

  private ResetCandidate resetCandidate(UserEntity user) {
    boolean eligible = user.isEnabled() && user.getDeletedAt() == null && user.getEmail() != null
        && selfServiceRole.equals(user.getRole());
    return new ResetCandidate(user.getId(), user.getUsername(), user.getEmail(), eligible);
  }

  /**
   * Sets the account's password as a Password reset completes, records it in Password history, and ends any Login
   * lockout. Joins the caller's transaction, so the reset token is spent in the same commit. Never touches
   * {@code last_login_at}.
   *
   * @param passwordHash the new password, already hashed with the application's encoder
   */
  @Transactional
  public void resetPassword(UUID id, String passwordHash, Instant now) {
    if (userRepository.resetPassword(id, passwordHash, now) != 1) {
      throw new IllegalStateException("Password reset target account is missing.");
    }
    passwordHistory.record(id, passwordHash);
  }

  /**
   * Records a successful login: inactivity for the account hygiene jobs is measured from this, and the Login lockout
   * failure count restarts.
   */
  @Transactional
  public void recordSuccessfulLogin(String username, Instant at) {
    userRepository.recordSuccessfulLogin(username, at);
  }

  /** True while the account is under a Login lockout; false for an unknown username. */
  @Transactional(readOnly = true)
  public boolean isLocked(String username, Instant now) {
    return userRepository.existsByUsernameAndLockedUntilAfter(username, now);
  }

  /**
   * Counts one failed Login attempt against the account and, when the count reaches the configured threshold, locks
   * it for the configured duration and restarts the count. A no-op for an unknown username or an account already
   * locked.
   *
   * @return true only when this failure set the lock
   */
  @Transactional
  public boolean recordFailedLogin(String username, Instant now) {
    if (userRepository.incrementFailedLoginAttempts(username, now) == 0) return false;
    return userRepository.lockWhenThresholdReached(
        username, lockout.threshold(), now.plus(lockout.duration()), now) == 1;
  }

  /**
   * Creates an enabled account, flushed immediately so the unique constraints are checked inside this call. Joins the
   * caller's transaction; a duplicate caught here leaves that transaction rollback-only, so the caller must not
   * commit other work in it after an empty result.
   *
   * <p>A {@code null} email (an Admin created by Admin bootstrap) is left out of the duplicate check: the pre-check
   * matches on username only, so an emailless account never collides with another emailless one. The database keeps
   * multiple NULL emails distinct, so the unique constraint agrees. Registration always passes an email, so its
   * behaviour is unchanged.
   *
   * @return the new account id, or empty when the username or email is already taken, including a concurrent insert.
   */
  @Transactional
  public Optional<UUID> createAccount(String username, String email, String passwordHash, String role) {
    boolean taken = email == null
        ? userRepository.existsByUsernameIgnoreCase(username)
        : userRepository.existsByUsernameIgnoreCaseOrEmailIgnoreCase(username, email);
    if (taken) return Optional.empty();
    var id = UUID.randomUUID();
    try {
      userRepository.saveAndFlush(new UserEntity(id, username, email, passwordHash, role, true));
      passwordHistory.record(id, passwordHash);
    } catch (DataIntegrityViolationException raceWithConcurrentRegistration) {
      return Optional.empty();
    }
    return Optional.of(id);
  }
}
