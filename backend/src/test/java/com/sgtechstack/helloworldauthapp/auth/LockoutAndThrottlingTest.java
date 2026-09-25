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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class LockoutAndThrottlingTest {

    private static final String USERNAME = "lockoutuser";
    private static final String PASSWORD = "correct-horse-battery";
    private static final String WRONG_PASSWORD = "definitely-not-the-password";

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
        // Clear tokens first: they FK-reference users, so a leftover token
        // row from another test class would otherwise block this delete.
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(
                new User(USERNAME, "lockoutuser@example.com", passwordEncoder.encode(PASSWORD), Role.USER, true));
        // Each test gets a fresh throttle state too, since it's an
        // in-memory singleton shared across the whole test JVM.
        resetThrottleFor(LOCAL_IP);
    }

    private static final String LOCAL_IP = "127.0.0.1";

    private void resetThrottleFor(String ip) {
        ipLoginThrottle.recordSuccess(ip);
    }

    private void attemptLoginWithWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .param("username", USERNAME)
                .param("password", WRONG_PASSWORD));
    }

    @Test
    void accountLocksAfterMaxFailedAttempts() throws Exception {
        for (int i = 0; i < LockoutPolicy.MAX_FAILED_ATTEMPTS; i++) {
            attemptLoginWithWrongPassword();
        }

        User locked = userRepository.findByUsernameIgnoreCase(USERNAME).orElseThrow();
        assertThat(locked.getFailedLoginAttempts()).isEqualTo(LockoutPolicy.MAX_FAILED_ATTEMPTS);
        assertThat(locked.getLockedUntil()).isNotNull();
        assertThat(locked.getLockedUntil()).isAfter(Instant.now());
    }

    @Test
    void lockedAccountRejectsCorrectPasswordUntilCooldownExpires() throws Exception {
        for (int i = 0; i < LockoutPolicy.MAX_FAILED_ATTEMPTS; i++) {
            attemptLoginWithWrongPassword();
        }

        // Correct password, but the account is still within its cooldown.
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .param("username", USERNAME)
                        .param("password", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));

        User stillLocked = userRepository.findByUsernameIgnoreCase(USERNAME).orElseThrow();
        assertThat(stillLocked.getLockedUntil()).isNotNull();
    }

    @Test
    void loginSucceedsAndResetsCounterOnceCooldownHasExpired() throws Exception {
        User user = userRepository.findByUsernameIgnoreCase(USERNAME).orElseThrow();
        user.setFailedLoginAttempts(LockoutPolicy.MAX_FAILED_ATTEMPTS);
        // Simulate the cooldown having already elapsed, rather than
        // waiting LockoutPolicy.LOCKOUT_DURATION in real time.
        user.setLockedUntil(Instant.now().minusSeconds(1));
        userRepository.save(user);

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .param("username", USERNAME)
                        .param("password", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(USERNAME));

        User reloaded = userRepository.findByUsernameIgnoreCase(USERNAME).orElseThrow();
        assertThat(reloaded.getFailedLoginAttempts()).isZero();
        assertThat(reloaded.getLockedUntil()).isNull();
    }

    @Test
    void ipThrottleEngagesIndependentlyOfAccountLockout() throws Exception {
        // Fail from the same IP against many different (non-existent)
        // usernames -- no single account ever reaches its own lockout
        // threshold, but the IP itself should still get throttled.
        for (int i = 0; i < IpLoginThrottle.MAX_FAILED_ATTEMPTS_PER_IP; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .with(csrf())
                    .param("username", "no-such-user-" + i)
                    .param("password", WRONG_PASSWORD));
        }

        // The throttle is now active for this IP, independent of any
        // account's lockout state -- even a fresh, unrelated, correctly
        // spelled account+password gets rejected at the IP layer.
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .param("username", USERNAME)
                        .param("password", PASSWORD))
                .andExpect(status().is(429))
                .andExpect(jsonPath("$.message")
                        .value("Too many failed login attempts from this network. Try again shortly."));

        // Confirm the account itself was never locked by this.
        User user = userRepository.findByUsernameIgnoreCase(USERNAME).orElseThrow();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getFailedLoginAttempts()).isZero();
    }
}
