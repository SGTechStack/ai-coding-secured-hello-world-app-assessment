package com.example.demo_app.user;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists the lockout bookkeeping of a login ({@link UserAccount#registerFailedLogin}, {@link
 * UserAccount#registerSuccessfulLogin}). Each call is its own transaction ({@code REQUIRES_NEW}),
 * so a failure is counted even though the login itself is rejected, and it row-locks the account
 * so concurrent failures are all counted.
 */
@Service
@EnableConfigurationProperties(LockoutProperties.class)
public class LoginAttempts {

  private final UserAccountRepository accounts;
  private final LockoutProperties lockout;
  private final Clock clock;

  public LoginAttempts(UserAccountRepository accounts, LockoutProperties lockout, Clock clock) {
    this.accounts = accounts;
    this.lockout = lockout;
    this.clock = clock;
  }

  /**
   * Counts a wrong password for {@code username} (as submitted; an unknown name is ignored).
   *
   * @return whether this failure locked the account
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean recordFailure(String username) {
    return accounts
        .findByUsernameForUpdate(UserAccount.normaliseUsername(username))
        .map(
            account ->
                account.registerFailedLogin(
                    clock.instant(), lockout.maxFailures(), lockout.duration()))
        .orElse(false);
  }

  /** Resets the failure count and lock of {@code username} after a successful login. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordSuccess(String username) {
    accounts
        .findByUsernameForUpdate(UserAccount.normaliseUsername(username))
        .ifPresent(UserAccount::registerSuccessfulLogin);
  }
}
