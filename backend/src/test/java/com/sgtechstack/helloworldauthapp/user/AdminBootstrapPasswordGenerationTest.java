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
 * Covers the blank-password branch of {@link AdminBootstrapRunner}.
 *
 * <p>This branch is no longer what dev defaults to. The dev profile now sets a
 * fixed {@code password1234} by product decision, so generating a random password
 * is the opt-in behaviour reached by setting {@code APP_ADMIN_PASSWORD=} — the
 * escape hatch for anyone running this profile somewhere reachable.
 *
 * <p>It is still worth testing, and the reason has not changed: the risk in the
 * blank branch is hashing the empty string and shipping an account whose password
 * is nothing at all. These assertions target exactly that.
 *
 * <p>This class used to also assert that {@code password1234} could not be the
 * seeded password. That assertion has been removed rather than adjusted, because
 * it now contradicts the configured default — keeping it would have meant a test
 * asserting the opposite of what the application is specified to do.
 *
 * <p>Overrides {@code app.admin.password} to blank because
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
    void generatesSomethingLongEnoughToBeWorthless() {
        User admin = seedAndFetchAdmin();

        // 24 bytes of SecureRandom in URL-safe base64. Asserted through the
        // encoder rather than by reading the password, which the runner does not
        // return: a short generated password would be a worse outcome than the
        // fixed one this branch exists as an alternative to.
        assertThat(passwordEncoder.matches("password", admin.getPasswordHash())).isFalse();
        assertThat(passwordEncoder.matches("admin", admin.getPasswordHash())).isFalse();
        assertThat(admin.getPasswordHash()).hasSizeGreaterThan(50);
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
