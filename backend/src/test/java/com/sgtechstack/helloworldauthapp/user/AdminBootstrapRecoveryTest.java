package com.sgtechstack.helloworldauthapp.user;

import com.sgtechstack.helloworldauthapp.passwordreset.PasswordResetTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The bootstrap runner has to recover a system with no administrator who can
 * sign in — not merely one with no {@code ADMIN} row.
 *
 * <h2>The bug</h2>
 *
 * The skip condition was {@code existsByRole(ADMIN)}. A disabled {@code ADMIN}
 * row satisfies it while being unable to authenticate, so a system whose only
 * administrator had been disabled looked bootstrapped and got nothing. Combined
 * with the missing last-admin guard, that meant a state the application could
 * produce and could not escape: no usable administrator, no way to create one,
 * recoverable only by editing the database by hand — the exact situation the
 * bootstrap story exists to remove.
 *
 * <p>The condition is now {@code existsByRoleAndEnabledTrue}, and because that
 * lets seeding run while the configured username is already taken, the existing
 * row is revived rather than duplicated.
 *
 * <p>Reviving only applies to rows that are already {@code ADMIN}. If the
 * configured username belongs to a regular account, startup refuses: promoting it
 * would be admin rights granted by one environment variable, bypassing {@code
 * RoleMutationGuard} and leaving no audit record. Asserted below, because
 * "refuses to help" is a deliberate behaviour and would otherwise look like the
 * bug returning.
 */
@SpringBootTest
@ActiveProfiles("dev")
class AdminBootstrapRecoveryTest {

    @Autowired
    private AdminBootstrapRunner adminBootstrapRunner;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${app.admin.username}")
    private String configuredAdminUsername;

    @Value("${app.admin.password}")
    private String configuredAdminPassword;

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void aDisabledAdminNoLongerCountsAsBootstrapped() {
        userRepository.save(new User(configuredAdminUsername, "disabled-admin@example.com",
                passwordEncoder.encode("some-old-password"), Role.ADMIN, false));

        adminBootstrapRunner.run(new DefaultApplicationArguments());

        User recovered = userRepository.findByUsernameIgnoreCase(configuredAdminUsername).orElseThrow();
        assertThat(recovered.isEnabled())
                .as("with no enabled admin, a restart must restore one rather than skipping")
                .isTrue();
    }

    @Test
    void theRevivedAccountAcceptsTheConfiguredPassword() {
        userRepository.save(new User(configuredAdminUsername, "disabled-admin@example.com",
                passwordEncoder.encode("a-password-nobody-remembers"), Role.ADMIN, false));

        adminBootstrapRunner.run(new DefaultApplicationArguments());

        User recovered = userRepository.findByUsernameIgnoreCase(configuredAdminUsername).orElseThrow();
        // Re-enabling an account whose password is lost would recover nothing.
        assertThat(passwordEncoder.matches(configuredAdminPassword, recovered.getPasswordHash())).isTrue();
    }

    @Test
    void theRevivedAccountIsNotStillLockedOut() {
        User locked = new User(configuredAdminUsername, "disabled-admin@example.com",
                passwordEncoder.encode("irrelevant"), Role.ADMIN, false);
        locked.setFailedLoginAttempts(5);
        locked.setLockedUntil(Instant.now().plus(1, ChronoUnit.HOURS));
        locked.setLastFailedLoginAt(Instant.now());
        userRepository.save(locked);

        adminBootstrapRunner.run(new DefaultApplicationArguments());

        User recovered = userRepository.findByUsernameIgnoreCase(configuredAdminUsername).orElseThrow();
        // An account disabled after a run of failed logins would otherwise come
        // back enabled and still locked, which looks exactly like the bootstrap
        // not having worked.
        assertThat(recovered.getLockedUntil()).isNull();
        assertThat(recovered.getFailedLoginAttempts()).isZero();
        assertThat(recovered.getLastFailedLoginAt()).isNull();
    }

    @Test
    void revivingDoesNotCreateASecondRowForTheSameUsername() {
        userRepository.save(new User(configuredAdminUsername, "disabled-admin@example.com",
                passwordEncoder.encode("irrelevant"), Role.ADMIN, false));

        adminBootstrapRunner.run(new DefaultApplicationArguments());

        // The username column is unique, so an insert here would fail outright —
        // which is why the revive branch exists at all rather than being an
        // optimisation.
        assertThat(userRepository.findAllByOrderByCreatedAtAsc()).hasSize(1);
    }

    @Test
    void anEnabledAdminUnderADifferentNameStillCountsAsBootstrapped() {
        userRepository.save(new User("someone-else", "someone-else@example.com",
                passwordEncoder.encode("irrelevant"), Role.ADMIN, true));

        adminBootstrapRunner.run(new DefaultApplicationArguments());

        // Recovery must not mean "always ensure the configured account exists".
        // A working administrator is a working administrator, whatever it is
        // called, and seeding a second one would be an unrequested extra
        // privileged account on every restart.
        assertThat(userRepository.findAllByOrderByCreatedAtAsc()).hasSize(1);
        assertThat(userRepository.findByUsernameIgnoreCase(configuredAdminUsername)).isEmpty();
    }

    @Test
    void refusesToPromoteANonAdminHoldingTheConfiguredUsername() {
        userRepository.save(new User(configuredAdminUsername, "regular@example.com",
                passwordEncoder.encode("their-own-password"), Role.USER, true));

        adminBootstrapRunner.run(new DefaultApplicationArguments());

        User untouched = userRepository.findByUsernameIgnoreCase(configuredAdminUsername).orElseThrow();
        // Promoting would make admin rights obtainable by setting one environment
        // variable to an existing user's name — no audit record, no route through
        // RoleMutationGuard. Refusing to recover is the lesser harm, and it is
        // logged at ERROR with what to do instead.
        assertThat(untouched.getRole()).isEqualTo(Role.USER);
        assertThat(userRepository.countByRoleAndEnabledTrue(Role.ADMIN)).isZero();
    }
}
