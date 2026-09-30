package com.example.hello.auth;

import com.example.hello.user.User;
import com.example.hello.user.UserRepository;
import java.time.Clock;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Adapts the {@code users} table to Spring Security, including enabled and lock state. */
@Service
public class AppUserDetailsService implements UserDetailsService {

  private final UserRepository userRepository;
  private final Clock clock;

  public AppUserDetailsService(UserRepository userRepository, Clock clock) {
    this.userRepository = userRepository;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    User user =
        userRepository
            .findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    return org.springframework.security.core.userdetails.User.withUsername(user.getUsername())
        .password(user.getPasswordHash())
        .authorities(user.getRole().authority())
        .disabled(!user.isEnabled())
        .accountLocked(user.isLockedAt(clock.instant()))
        .build();
  }
}
