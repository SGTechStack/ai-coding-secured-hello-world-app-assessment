package com.assessment.auth.user;

import com.assessment.auth.common.ApiErrorCode;
import com.assessment.auth.common.ApiException;
import com.assessment.auth.password.PasswordHistoryService;
import com.assessment.auth.password.PasswordPolicy;
import java.time.Clock;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one place an account comes into existence (stories 1.5, 1.15, 1.21).
 *
 * <p>Registration, administrator creation and the bootstrap seed all route through here, so the
 * four obligations that apply to every new account — uniqueness against live users
 * <em>and</em> tombstones, the password policy, the BCrypt hash, and the first password-history
 * row — cannot be satisfied on one path and forgotten on another.
 *
 * <p><strong>Both username and email are checked against tombstones.</strong> The source recipe
 * checks only username (Priv:149), which is a bug: Std:96 states email uniqueness unqualified, so
 * a deleted account's email would otherwise be re-registrable while its username was not.
 */
@Service
public class AccountCreationService {

  private final UserRepository userRepository;
  private final DeletedUserRepository deletedUserRepository;
  private final PasswordPolicy passwordPolicy;
  private final PasswordHistoryService passwordHistoryService;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;

  public AccountCreationService(
      UserRepository userRepository,
      DeletedUserRepository deletedUserRepository,
      PasswordPolicy passwordPolicy,
      PasswordHistoryService passwordHistoryService,
      PasswordEncoder passwordEncoder,
      Clock clock) {
    this.userRepository = userRepository;
    this.deletedUserRepository = deletedUserRepository;
    this.passwordPolicy = passwordPolicy;
    this.passwordHistoryService = passwordHistoryService;
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
  }

  /**
   * Creates an account.
   *
   * @param requirePasswordChange true for administrator-created accounts and the bootstrap seed, so
   *     the new user's first action after logging in is choosing their own password
   */
  @Transactional
  public User create(
      String username, String email, String rawPassword, String role, boolean requirePasswordChange) {

    assertIdentifiersAvailable(username, email);
    passwordPolicy.validate(rawPassword, username, email);

    String hash = passwordEncoder.encode(rawPassword);
    User user =
        new User(
            UUID.randomUUID(),
            username,
            email,
            hash,
            role,
            true,
            requirePasswordChange,
            clock.instant());
    User saved = userRepository.save(user);
    // The first history row. Without it the account's current password would not be blocked as a
    // "reuse" on the very next change, because the newest history row IS the current hash.
    passwordHistoryService.record(saved.getId(), hash);
    return saved;
  }

  /**
   * Rejects an identifier held by a live user or burned by a tombstone.
   *
   * <p>The error names which of the two collided, because this is a usability concern on a
   * registration form and not an authentication outcome — Std:247's indistinguishability clause
   * scopes to authentication, and applying it here would leave a user unable to tell why their
   * registration failed.
   */
  public void assertIdentifiersAvailable(String username, String email) {
    if (userRepository.existsByUsername(username) || deletedUserRepository.existsByUsername(username)) {
      throw new ApiException(ApiErrorCode.IDENTIFIER_UNAVAILABLE, "That username is not available.");
    }
    if (userRepository.existsByEmail(email) || deletedUserRepository.existsByEmail(email)) {
      throw new ApiException(ApiErrorCode.IDENTIFIER_UNAVAILABLE, "That email is not available.");
    }
  }
}
