package com.example.auth.security;

import com.example.auth.user.Role;
import com.example.auth.user.User;
import com.example.auth.user.UserRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Backs Spring Security's authentication with the existing {@code User} JPA
 * entity / {@link UserRepository}, without touching either. Authorities are
 * derived from {@code User#getRole()}: {@code ADMIN} gets both {@code
 * ROLE_ADMIN} and {@code ROLE_USER} (so an admin-only check plus a
 * broader-authenticated check both pass for an admin), while {@code USER}
 * gets just {@code ROLE_USER}.
 *
 * <p>{@code disabled} is derived from {@code User#isEnabled}, and {@code
 * accountLocked} from {@code User#isLocked}: when either is true, {@code
 * DaoAuthenticationProvider}'s pre-authentication checks reject the login
 * with {@code DisabledException}/{@code LockedException} <em>before</em>
 * comparing the password -- a correct password still fails, which is the
 * point of both the disabled-account and account-lockout requirements.
 * {@code SecurityConfig}'s failure handler doesn't discriminate by exception
 * type, so this still surfaces as the same generic {@code 401} body
 * (anti-enumeration holds).
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository
                .findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("No user with username: " + username));

        List<SimpleGrantedAuthority> authorities = user.getRole() == Role.ADMIN
                ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_USER"))
                : List.of(new SimpleGrantedAuthority("ROLE_USER"));

        return org.springframework.security.core.userdetails.User.withUsername(user.getUsername())
                .password(user.getPassword())
                .authorities(authorities)
                .disabled(!user.isEnabled())
                .accountLocked(user.isLocked(Instant.now()))
                .build();
    }
}
