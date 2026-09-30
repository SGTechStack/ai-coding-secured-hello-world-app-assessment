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
 * Backs Spring Security's authentication with the {@code User} JPA entity. Authorities are derived
 * from {@code User#getRole()}: {@code ADMIN} gets both {@code ROLE_ADMIN} and {@code ROLE_USER},
 * {@code USER} just {@code ROLE_USER}.
 *
 * <p>{@code disabled} comes from {@code User#isEnabled} and {@code accountLocked} from {@code
 * User#isLocked}. {@code SecurityConfig} configures {@code DaoAuthenticationProvider} to check those
 * flags only <em>after</em> the password comparison, so unknown, wrong-password, locked and
 * disabled logins all cost one BCrypt verification and look identical from outside (same 401 body,
 * same timing).
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Deliberately no username in the message: it would end up in logs via the exception.
        User user = userRepository.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("Unknown user"));

        List<SimpleGrantedAuthority> authorities = user.getRole() == Role.ADMIN
                ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_USER"))
                : List.of(new SimpleGrantedAuthority("ROLE_USER"));

        return new AppUserDetails(
                user.getPublicId(),
                user.getUsername(),
                user.getPassword(),
                user.isEnabled(),
                !user.isLocked(Instant.now()),
                authorities);
    }
}
