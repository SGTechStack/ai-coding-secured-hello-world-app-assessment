package com.example.demo_app.user;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads the principal for a login. The submitted username is normalised first, so the lookup is
 * case-insensitive against the lowercase stored value.
 */
@Service
public class AccountUserDetailsService implements UserDetailsService {

  private final UserAccountRepository accounts;

  public AccountUserDetailsService(UserAccountRepository accounts) {
    this.accounts = accounts;
  }

  @Override
  @Transactional(readOnly = true)
  public AccountUserDetails loadUserByUsername(String username) {
    return accounts
        .findByUsername(UserAccount.normaliseUsername(username))
        .map(AccountUserDetails::new)
        .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
  }
}
