package com.sgtechstack.helloworldauthapp.auth;

import com.sgtechstack.helloworldauthapp.passwordreset.PasswordResetTokenRepository;
import com.sgtechstack.helloworldauthapp.user.Role;
import com.sgtechstack.helloworldauthapp.user.User;
import com.sgtechstack.helloworldauthapp.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The failed-attempt counter must decay, so that only failures genuinely
 * <em>within</em> the window accumulate.
 *
 * <p>Before this, {@code failed_login_attempts} was a lifetime tally that reset
 * only on a successful login. Five failures spread across months still locked
 * the account, which made targeted lockout trivial: five cheap requests deny a
 * known username access, repeatable whenever the cooldown lapses, and five
 * stays under the per-IP threshold so IP throttling never engages. The PRD asks
 * for "N consecutive failed attempts within a window" for exactly this reason.
 *
 * <p>Ageing a failure is done by writing {@code last_failed_login_at} directly
 * rather than waiting 15 minutes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class LockoutWindowTest {

    private static final String USERNAME = "windowuser";
    private static final String PASSWORD = "correct-horse-battery";
    private static final String MOCK_MVC_REMOTE_ADDR = "127.0.0.1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private IpLoginThrottle ipLoginThrottle;

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(new User(
                USERNAME, "windowuser@example.com", passwordEncoder.encode(PASSWORD), Role.USER, true));

        // The throttle is a singleton shared across the cached Spring context,
        // and every MockMvc request arrives from the same address, so failures
        // from earlier methods (and other classes) accumulate against it and
        // would answer 429 instead of the 401 these tests are about. Clearing it
        // keeps this class measuring account lockout rather than IP throttling,
        // which LockoutAndThrottlingTest covers separately.
        ipLoginThrottle.recordSuccess(MOCK_MVC_REMOTE_ADDR);
    }

    private void failLogin() throws Exception {
        // Reset the IP counter before each attempt: several of these tests need
        // more failures than the per-IP threshold allows, and the two controls
        // are independent by design.
        ipLoginThrottle.recordSuccess(MOCK_MVC_REMOTE_ADDR);

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .param("username", USERNAME)
                        .param("password", "wrong-password"))
                .andExpect(status().isUnauthorized());
    }

    private User reload() {
        return userRepository.findByUsernameIgnoreCase(USERNAME).orElseThrow();
    }

    /** Backdates the last failure so it falls outside the window. */
    private void ageLastFailureBeyondTheWindow() {
        User user = reload();
        user.setLastFailedLoginAt(Instant.now().minus(LockoutPolicy.FAILURE_WINDOW).minusSeconds(60));
        userRepository.save(user);
    }

    @Test
    void failuresInsideTheWindowAccumulate() throws Exception {
        failLogin();
        failLogin();

        assertThat(reload().getFailedLoginAttempts()).isEqualTo(2);
    }

    @Test
    void aFailureOutsideTheWindowStartsAFreshStreakInsteadOfAddingToTheOldOne() throws Exception {
        failLogin();
        failLogin();
        failLogin();
        failLogin();
        assertThat(reload().getFailedLoginAttempts()).isEqualTo(4);

        ageLastFailureBeyondTheWindow();
        failLogin();

        // Previously this fifth failure would have locked the account, however
        // long after the first four it arrived.
        assertThat(reload().getFailedLoginAttempts()).isEqualTo(1);
        assertThat(reload().getLockedUntil()).isNull();
    }

    @Test
    void anAccountIsStillLockedByEnoughFailuresInsideTheWindow() throws Exception {
        for (int i = 0; i < LockoutPolicy.MAX_FAILED_ATTEMPTS; i++) {
            failLogin();
        }

        // The decay must not weaken the control it bounds.
        assertThat(reload().getLockedUntil()).isNotNull();
    }

    @Test
    void staleFailuresCannotBeAccumulatedIndefinitelyToReachTheThreshold() throws Exception {
        // Four separate, widely-spaced failures. Under a lifetime tally these
        // would add up to a lockout; under a window each is its own streak.
        for (int i = 0; i < 4; i++) {
            failLogin();
            ageLastFailureBeyondTheWindow();
        }

        assertThat(reload().getFailedLoginAttempts()).isEqualTo(1);
        assertThat(reload().getLockedUntil()).isNull();
    }

    @Test
    void successfulLoginClearsTheStreakMarker() throws Exception {
        failLogin();
        assertThat(reload().getLastFailedLoginAt()).isNotNull();

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .param("username", USERNAME)
                        .param("password", PASSWORD))
                .andExpect(status().isOk());

        User user = reload();
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLastFailedLoginAt()).isNull();
    }
}
