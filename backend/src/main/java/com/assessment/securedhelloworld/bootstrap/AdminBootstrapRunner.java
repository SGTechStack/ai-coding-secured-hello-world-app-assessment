package com.assessment.securedhelloworld.bootstrap;

import com.assessment.securedhelloworld.user.Role;
import com.assessment.securedhelloworld.user.User;
import com.assessment.securedhelloworld.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds an initial ADMIN account on startup if none exists yet (Story 12).
 * Idempotent: does nothing on a restart where an ADMIN user already
 * exists, so no duplicate seed account is ever created. The seeded
 * account is created with {@code forcePasswordChange = true} so the
 * bootstrap credential can never be used beyond the very first login
 * (IM8 ac-6) — {@code ForcePasswordChangeFilter} blocks all other
 * endpoints until the password is changed.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminBootstrapProperties properties;

    public AdminBootstrapRunner(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AdminBootstrapProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        run();
    }

    public void run() {
        if (userRepository.countByRole(Role.ADMIN) > 0) {
            return;
        }

        User admin = new User(
                properties.getUsername(),
                properties.getUsername() + "@bootstrap.local",
                passwordEncoder.encode(properties.getPassword()));
        admin.setRole(Role.ADMIN);
        admin.setForcePasswordChange(true);
        userRepository.save(admin);

        log.info("Seeded initial admin account username={}", admin.getUsername());
    }
}
