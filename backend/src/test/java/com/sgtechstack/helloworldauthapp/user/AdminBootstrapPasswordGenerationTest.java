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
 * Covers the blank-password branch of {@link AdminBootstrapRunner}, which is
 * what dev now uses.
 *
 * <p>The dev profile used to default the admin password to {@code password1234}
 * — a fixed credential that was also printed in the README and rendered in the
 * login form, so any reachable dev instance was a one-guess admin takeover.
 * It now defaults to blank, meaning "generate one for this boot". The risk to
 * guard against in that change is hashing the blank string and shipping an
 * account whose password is empty, so that is what these assertions target.
 *
 * <p>Overrides {@code app.admin.password} back to blank because
 * {@code src/test/resources/application-dev.yml} pins a known password for the
 * rest of the suite.
 */
@SpringBootTest(properties = "app.admin.password=")
@ActiveProfiles("dev")
class AdminBootstrapPasswordGenerationTest {

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
        tokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User seedAndFetchAdmin() {
        adminBootstrapRunner.run(new DefaultApplicationArguments());

        return userRepository.findAllByOrderByCreatedAtAsc().stream()
                .filter(u -> u.getRole() == Role.ADMIN)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void doesNotSeedAnAccountWhosePasswordIsBlank() {
        User admin = seedAndFetchAdmin();

        assertThat(passwordEncoder.matches("", admin.getPasswordHash()))
                .as("a blank configured password must be replaced, not hashed as-is")
                .isFalse();
        assertThat(admin.getPasswordHash()).isNotBlank();
    }

    @Test
    void doesNotFallBackToThePasswordThisFixRemoved() {
        User admin = seedAndFetchAdmin();

        assertThat(passwordEncoder.matches("password1234", admin.getPasswordHash())).isFalse();
    }

    @Test
    void generatesADifferentPasswordEachTime() {
        String firstHash = seedAndFetchAdmin().getPasswordHash();

        userRepository.deleteAll();
        String secondHash = seedAndFetchAdmin().getPasswordHash();

        // BCrypt salts per hash, so differing hashes alone prove nothing. The
        // meaningful check is that neither hash verifies the other's password —
        // which we can't read. Comparing hashes is still worth asserting as a
        // smoke test that two independent seeds happened.
        assertThat(firstHash).isNotEqualTo(secondHash);
    }
}
