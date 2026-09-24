package com.example.helloauth.user;

import java.time.Clock;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Maps {@link User} rows onto Spring Security's {@link UserDetails}.
 *
 * <p>{@code locked_until} maps to {@code isAccountNonLocked()} — the
 * DaoAuthenticationProvider's pre-authentication check throws
 * {@code LockedException} before password verification, so a locked account
 * rejects even correct credentials (per spec). {@code enabled} maps to
 * {@code isEnabled()} → {@code DisabledException}. Both still surface to the
 * client as the generic 401.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository users;
    private final Clock clock;

    public AppUserDetailsService(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = users.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException(username));

        boolean locked = user.getLockedUntil() != null
            && user.getLockedUntil().isAfter(clock.instant());

        return org.springframework.security.core.userdetails.User.builder()
            .username(user.getUsername())
            .password(user.getPasswordHash())
            .roles(user.getRole().name())
            .accountLocked(locked)
            .disabled(!user.isEnabled())
            .build();
    }
}
