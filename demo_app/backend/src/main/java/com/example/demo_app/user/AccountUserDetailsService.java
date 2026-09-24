package com.example.demo_app.user;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        .findByUsername(username)
        .map(AccountUserDetails::new)
        .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
  }
}
