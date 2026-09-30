package org.eds.demo.user.application;

import lombok.RequiredArgsConstructor;
import org.eds.demo.user.domain.AppUserDetails;
import org.eds.demo.user.infrastructure.AppUserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads an existing Account with its stored password hash for credential checks. Never creates an
 * Account: unknown usernames, and Accounts that have no password set, are reported as not found.
 */
@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

  private final AppUserRepository appUserRepository;

  @Override
  @Transactional(readOnly = true)
  public AppUserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    var appUser =
        appUserRepository
            .findByUsername(username)
            .filter(account -> account.getPasswordHash() != null)
            .orElseThrow(() -> new UsernameNotFoundException("No sign-in Account for username"));

    var authorities =
        appUser.getRoles().stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
            .toList();

    return new AppUserDetails(
        appUser.getId(),
        appUser.getUsername(),
        appUser.getUsername(),
        appUser.getPasswordHash(),
        appUser.isEnabled(),
        authorities);
  }
}
