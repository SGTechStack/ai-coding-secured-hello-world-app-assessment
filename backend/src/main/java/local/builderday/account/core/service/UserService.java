package local.builderday.account.core.service;

import local.builderday.account.core.model.AccountPrincipal;
import local.builderday.account.core.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Provides the user credentials Spring Security needs for password authentication. */
@Service
public class UserService implements UserDetailsService {
  private final UserRepository userRepository;

  public UserService(UserRepository userRepository) { this.userRepository = userRepository; }

  @Override
  public UserDetails loadUserByUsername(String username) {
    var user = userRepository.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    // A soft-deleted account is a tombstone: it can never authenticate, even if its enabled flag were set.
    // Spring Security's own User is fully qualified to avoid the clash with the domain User model (ADR 0011). The
    // principal adds only the Account's id, for request attribution; authorities and status are exactly the builder's.
    var details = org.springframework.security.core.userdetails.User.withUsername(user.getUsername())
        .password(user.getPasswordHash()).roles(user.getRole())
        .disabled(!user.isEnabled() || user.getDeletedAt() != null).build();
    return new AccountPrincipal(user.getId(), details);
  }
}
