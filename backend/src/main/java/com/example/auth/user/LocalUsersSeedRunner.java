package com.example.auth.user;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Dev-only convenience: seeds any extra local accounts declared under {@code
 * app.local.users} that don't already exist yet, by username. Separate from
 * {@link AdminBootstrapRunner} on purpose -- admin bootstrap stays a
 * single-admin-exists check; this seeds whichever configured entries are
 * still missing, independently of each other and of the ADMIN account.
 */
@Component
@Order(2)
public class LocalUsersSeedRunner implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LocalUsersProperties localUsersProperties;

    public LocalUsersSeedRunner(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            LocalUsersProperties localUsersProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.localUsersProperties = localUsersProperties;
    }

    @Override
    public void run(String... args) {
        for (LocalUsersProperties.UserEntry entry : localUsersProperties.users()) {
            if (userRepository.existsByUsername(entry.username())) {
                continue;
            }
            if (!PasswordPolicy.isAcceptable(entry.password())) {
                throw new IllegalStateException("app.local.users password for a seeded user: " + PasswordPolicy.MESSAGE);
            }
            String email = entry.username() + "@localhost";
            User user =
                    new User(entry.username(), email, passwordEncoder.encode(entry.password()), entry.username());
            user.setRole(entry.role());
            userRepository.save(user);
        }
    }
}
