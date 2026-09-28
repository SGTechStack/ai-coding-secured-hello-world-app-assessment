package com.example.helloworldauth.auth;

import com.example.helloworldauth.user.User;
import com.example.helloworldauth.user.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/** Loads accounts from the users table for Spring Security authentication. */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public AppUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = users.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("not found"));

        boolean nonLocked = user.getLockedUntil() == null || Instant.now().isAfter(user.getLockedUntil());

        return org.springframework.security.core.userdetails.User
            .withUsername(user.getUsername())
            .password(user.getPasswordHash())
            .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
            .disabled(!user.isEnabled())
            .accountLocked(!nonLocked)
            .build();
    }
}
