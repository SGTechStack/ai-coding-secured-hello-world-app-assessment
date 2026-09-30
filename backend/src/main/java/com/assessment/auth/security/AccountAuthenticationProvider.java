package com.assessment.auth.security;

import com.assessment.auth.audit.AuditAction;
import com.assessment.auth.audit.AuditEvent;
import com.assessment.auth.audit.AuditLogger;
import com.assessment.auth.audit.AuditReason;
import com.assessment.auth.common.AuthenticatedUser;
import com.assessment.auth.user.User;
import com.assessment.auth.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.event.Level;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Verifies a credential and maintains the lockout counter (spec.md S4, S6).
 *
 * <p><strong>Every failure mode throws the same exception.</strong> An unknown username, a wrong
 * password, a locked account and a disabled account are indistinguishable to the caller — Std:258
 * and :259 for the lock and the disable, Std:247 for the rest — and the filter turns all of them
 * into one generic 401 with an empty body.
 *
 * <p>A locked account still returns the generic 401 rather than a 429. Lockout and rate limiting
 * produce <em>different</em> statuses by design: Std:247 scopes to authentication outcomes, while a
 * rate-limit rejection happens before authentication and is therefore free to be explicit (spec.md
 * S6).
 *
 * <p>The password is compared even when no user was found, so the response time does not fork on
 * whether the account exists. The filter's response-time floor covers the rest.
 */
@Component
public class AccountAuthenticationProvider implements AuthenticationProvider {

  /**
   * Compared against when no user was found, so the BCrypt cost is paid either way. A real cost-12
   * hash of a value no caller can supply.
   */
  private static final String ABSENT_USER_HASH =
      "$2a$12$C6UzMDM.H6dfI/f/IKcEe.7QKvvXQ8bNqRSsxfbMPbsxAPyKzE5Nu";

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final LockoutProperties lockoutProperties;
  private final AuditLogger auditLogger;
  private final Clock clock;

  public AccountAuthenticationProvider(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      LockoutProperties lockoutProperties,
      AuditLogger auditLogger,
      Clock clock) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.lockoutProperties = lockoutProperties;
    this.auditLogger = auditLogger;
    this.clock = clock;
  }

  /**
   * <strong>Deliberately not {@code @Transactional}.</strong>
   *
   * <p>Every failure path here throws {@link BadCredentialsException}, and a runtime exception
   * marks a surrounding transaction for rollback — which would discard the failed-login counter
   * increment that lockout is built on. The account would then never lock, and nothing else would
   * fail. Each {@code save} commits in its own repository-level transaction instead.
   */
  @Override
  public Authentication authenticate(Authentication authentication) throws AuthenticationException {
    String username = String.valueOf(authentication.getPrincipal());
    String password = String.valueOf(authentication.getCredentials());
    Instant now = clock.instant();

    Optional<User> found = userRepository.findByUsername(username);
    if (found.isEmpty()) {
      passwordEncoder.matches(password, ABSENT_USER_HASH);
      throw generic();
    }

    User user = found.get();

    // Checked BEFORE the credential: a locked account must not have its counter advanced further,
    // or a persistent attacker would extend the lock indefinitely with every attempt.
    if (user.isLockedAt(now)) {
      throw generic();
    }

    if (!passwordEncoder.matches(password, user.getPasswordHash())) {
      registerFailure(user, now);
      throw generic();
    }

    // Checked after the credential, so "disabled account" and "wrong password" take the same path
    // and the same time (Std:85, :259).
    if (!user.isEnabled()) {
      throw generic();
    }

    // The counter decays only on a successful login -- there is no time window (spec.md S6).
    user.setFailedLoginAttempts(0);
    user.setLockedUntil(null);
    user.setLastLoginAt(now);
    userRepository.save(user);

    AuthenticatedUser principal =
        new AuthenticatedUser(
            user.getId(),
            user.getUsername(),
            user.getPasswordHash(),
            user.getRole(),
            user.isRequirePasswordChange());
    return new PasswordAuthenticationToken(principal, principal.getAuthorities());
  }

  private void registerFailure(User user, Instant now) {
    int attempts = user.getFailedLoginAttempts() + 1;
    user.setFailedLoginAttempts(attempts);
    if (attempts >= lockoutProperties.maxAttempts()) {
      user.setLockedUntil(now.plus(lockoutProperties.duration()));
      // ERROR with the full error triplet. Nothing threw here, and that is precisely the case the
      // custom encoder CREATES the nested `error` object for -- the recipe would strip these three
      // fields and they would vanish with no failing build (spec.md S11, S13 test 2).
      auditLogger.emit(
          AuditEvent.of(AuditAction.AUTHENTICATION, AuditReason.ACCOUNT_LOCKED, Level.ERROR)
              .outcome("failure")
              .target(user.getId())
              .error("423", "authentication", "Contact an administrator to unlock the account.")
              .build());
    }
    userRepository.save(user);
  }

  /** One exception for every failure mode. The message never reaches a caller. */
  private static BadCredentialsException generic() {
    return new BadCredentialsException("Authentication failed.");
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return PasswordAuthenticationToken.class.isAssignableFrom(authentication);
  }
}
