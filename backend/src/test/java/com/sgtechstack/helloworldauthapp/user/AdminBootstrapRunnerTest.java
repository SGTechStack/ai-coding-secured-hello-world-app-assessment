package com.sgtechstack.helloworldauthapp.user;

import com.sgtechstack.helloworldauthapp.passwordreset.PasswordResetTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The bootstrap runner already executes once for real when the Spring
 * context starts (that's how every other test in the suite ends up with
 * a seeded admin account at all). Re-invoking it directly here exercises
 * its idempotency — the actual behaviour this ticket cares about — without
 * needing multiple full application context restarts within one test run.
 */
@SpringBootTest
@ActiveProfiles("dev")
class AdminBootstrapRunnerTest {

    @Autowired
    private AdminBootstrapRunner adminBootstrapRunner;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        // Clear tokens first: they FK-reference users, so a leftover token
        // row from another test class would otherwise block this delete.
        tokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void seedsExactlyOneAdminWhenNoneExists() {
        adminBootstrapRunner.run(new DefaultApplicationArguments());

        long adminCount = userRepository.findAllByOrderByCreatedAtAsc()
                .stream()
                .filter(u -> u.getRole() == Role.ADMIN)
                .count();
        assertThat(adminCount).isEqualTo(1);
    }

    @Test
    void seededAdminPasswordIsHashedNotPlaintext() {
        adminBootstrapRunner.run(new DefaultApplicationArguments());

        User admin = userRepository.findAllByOrderByCreatedAtAsc()
                .stream()
                .filter(u -> u.getRole() == Role.ADMIN)
                .findFirst()
                .orElseThrow();

        assertThat(admin.getPasswordHash()).isNotEqualTo("change-this-admin-password");
        assertThat(passwordEncoder.matches("change-this-admin-password", admin.getPasswordHash())).isTrue();
    }

    @Test
    void doesNotSeedASecondAdminIfOneAlreadyExists() {
        adminBootstrapRunner.run(new DefaultApplicationArguments());
        adminBootstrapRunner.run(new DefaultApplicationArguments());

        long adminCount = userRepository.findAllByOrderByCreatedAtAsc()
                .stream()
                .filter(u -> u.getRole() == Role.ADMIN)
                .count();
        assertThat(adminCount).isEqualTo(1);
    }

    @Test
    void doesNotSeedAnAdminIfOneAlreadyExistsFromElsewhere() {
        userRepository.save(new User(
                "existing-admin", "existing-admin@example.com",
                passwordEncoder.encode("some-other-password"), Role.ADMIN, true));

        adminBootstrapRunner.run(new DefaultApplicationArguments());

        long adminCount = userRepository.findAllByOrderByCreatedAtAsc()
                .stream()
                .filter(u -> u.getRole() == Role.ADMIN)
                .count();
        assertThat(adminCount).isEqualTo(1);
    }
}
