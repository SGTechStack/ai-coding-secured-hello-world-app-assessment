package hello.desk.security;

import hello.desk.user.UserAccount;
import hello.desk.user.UserAccountRepository;
import java.time.Instant;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AccountUserDetailsService implements UserDetailsService {

    private final UserAccountRepository users;

    public AccountUserDetailsService(UserAccountRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        UserAccount user = users.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(username));
        boolean locked = user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now());
        return User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .disabled(!user.isEnabled())
                .accountLocked(locked)
                .authorities("ROLE_" + user.getRole().name())
                .build();
    }
}
