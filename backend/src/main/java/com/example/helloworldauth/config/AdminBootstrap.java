package com.example.helloworldauth.config;

import com.example.helloworldauth.user.Role;
import com.example.helloworldauth.user.User;
import com.example.helloworldauth.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a single ADMIN account on first startup so an operator can reach the
 * admin module without editing the database (Story 12).
 *
 * <p>Idempotent: if any ADMIN already exists the runner does nothing, so a
 * restart never creates a duplicate. The seeded password is BCrypt-hashed with
 * the shared {@link PasswordEncoder}, identically to any other account -- it is
 * never stored or logged in plaintext. If the configured password is blank or
 * missing (as it should be in a real environment until set), seeding is skipped
 * with a warning rather than creating an account with no usable credential.
 */
@Configuration
@EnableConfigurationProperties(AdminBootstrapProperties.class)
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger audit = LoggerFactory.getLogger("audit");
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AdminBootstrapProperties props;

    public AdminBootstrap(UserRepository users, PasswordEncoder passwordEncoder,
                          AdminBootstrapProperties props) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedIfAbsent();
    }

    /**
     * Creates the bootstrap ADMIN when none exists. Returns {@code true} if an
     * account was seeded, {@code false} if seeding was skipped (an ADMIN already
     * exists, or the password is unset).
     */
    @Transactional
    public boolean seedIfAbsent() {
        if (users.existsByRole(Role.ADMIN)) {
            log.debug("Admin bootstrap skipped: an ADMIN account already exists");
            return false;
        }
        if (!props.hasPassword()) {
            log.warn("Admin bootstrap skipped: app.admin.password is not set; "
                + "no ADMIN account was seeded");
            return false;
        }

        String username = (props.username() == null || props.username().isBlank())
            ? "admin" : props.username();
        String email = (props.email() == null || props.email().isBlank())
            ? "admin@example.com" : props.email();

        String hash = passwordEncoder.encode(props.password());
        User saved = users.save(new User(username, email, hash, Role.ADMIN));
        audit.info("admin bootstrap seeded username={}", saved.getUsername());
        return true;
    }
}
