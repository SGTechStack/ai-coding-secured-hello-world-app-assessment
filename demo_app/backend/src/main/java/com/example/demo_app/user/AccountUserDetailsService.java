package com.example.demo_app.user;

import java.time.Clock;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads the principal for a login. The submitted username is normalised first, so the lookup is
 * case-insensitive against the lowercase stored value. Whether the account is locked is decided
 * on the injected {@link Clock}.
 */
@Service
public class AccountUserDetailsService implements UserDetailsService {

  private final UserAccountRepository accounts;
  private final Clock clock;

  public AccountUserDetailsService(UserAccountRepository accounts, Clock clock) {
    this.accounts = accounts;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public AccountUserDetails loadUserByUsername(String username) {
    return accounts
        .findByUsername(UserAccount.normaliseUsername(username))
        .map(account -> new AccountUserDetails(account, clock.instant()))
        .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
  }
}
