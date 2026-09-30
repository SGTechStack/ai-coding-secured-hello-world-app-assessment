package com.example.hello.auth;

import com.example.hello.common.AuditLogger;
import com.example.hello.config.AppSecurityProperties;
import com.example.hello.user.User;
import com.example.hello.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Story 3, account half: counts consecutive failures per account and sets {@code locked_until}
 * at the threshold. Failures against unknown usernames are ignored here (the IP throttle
 * covers those).
 */
@Service
public class AccountLockoutService {

  private final UserRepository userRepository;
  private final AppSecurityProperties.Login login;
  private final AuditLogger audit;
  private final Clock clock;

  public AccountLockoutService(
      UserRepository userRepository, AppSecurityProperties props, AuditLogger audit, Clock clock) {
    this.userRepository = userRepository;
    this.login = props.login();
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional
  public void onFailure(String username) {
    userRepository
        .findByUsername(username)
        .ifPresent(
            user -> {
              Instant now = clock.instant();
              if (user.hasExpiredLock(now)) {
                // A previous cooldown elapsed: start a fresh window instead of re-locking
                // on the very next mistake.
                user.resetFailedLogins();
              }
              int attempts = user.recordFailedLogin();
              if (attempts >= login.maxFailedAttempts()) {
                user.lockUntil(now.plus(login.lockoutDuration()));
                audit.event(
                    "ACCOUNT_LOCKED",
                    "username", user.getUsername(),
                    "attempts", String.valueOf(attempts),
                    "locked_until", user.getLockedUntil().toString());
              }
            });
  }

  @Transactional
  public void onSuccess(String username) {
    userRepository.findByUsername(username).ifPresent(User::resetFailedLogins);
  }
}
