package com.example.helloworldauth.auth;

import com.example.helloworldauth.user.Role;
import com.example.helloworldauth.user.User;
import com.example.helloworldauth.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 3 (ticket 07): account lockout after N failures, reset after cooldown,
 * and IP-level throttling independent of per-account lockout.
 *
 * <p>Each test uses a DISTINCT client IP so the in-memory {@link IpThrottlingService}
 * counter (a singleton across the shared Spring context) cannot leak between
 * tests: lockout tests stay well under the IP threshold of 10 and never collide
 * with the throttle test's IP.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LockoutThrottlingTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordEncoder encoder;

    private static final String PASSWORD = "correcthorsebattery";

    @BeforeEach
    void seed() {
        users.deleteAll();
        users.save(new User("alice", "alice@example.com", encoder.encode(PASSWORD), Role.USER));
    }

    private String body(String u, String p) {
        return """
            {"username":"%s","password":"%s"}""".formatted(u, p);
    }

    private void loginExpect(String username, String password, String ip, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/login").with(csrf())
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(username, password)))
            .andExpect(status().is(expectedStatus));
    }

    @Test
    void fiveFailuresLockAccountAndBlockCorrectPassword() throws Exception {
        String ip = "10.0.0.1";
        // 5 consecutive wrong-password failures on one account -> locked on the 5th.
        for (int i = 0; i < 5; i++) {
            loginExpect("alice", "wrongpassword!", ip, 401);
        }

        User locked = users.findByUsername("alice").orElseThrow();
        assertThat(locked.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(locked.getLockedUntil()).isNotNull();
        assertThat(locked.getLockedUntil()).isAfter(Instant.now());

        // Correct password is still rejected while the lock is active.
        loginExpect("alice", PASSWORD, ip, 401);
    }

    @Test
    void correctPasswordAfterCooldownSucceedsAndResetsCounter() throws Exception {
        String ip = "10.0.0.2";
        // Drive the account into a locked state, then simulate cooldown elapsing
        // by moving locked_until into the past.
        User u = users.findByUsername("alice").orElseThrow();
        u.setFailedLoginAttempts(5);
        u.setLockedUntil(Instant.now().minusSeconds(1));
        users.save(u);

        loginExpect("alice", PASSWORD, ip, 200);

        User after = users.findByUsername("alice").orElseThrow();
        assertThat(after.getFailedLoginAttempts()).isZero();
        assertThat(after.getLockedUntil()).isNull();
    }

    @Test
    void ipThrottlingTripsAcrossDifferentUsernamesIndependentOfAccountLock() throws Exception {
        String ip = "1.2.3.4";
        // Fail against MANY DIFFERENT usernames from one IP. None of these
        // accounts exist, so no single account is ever locked — the only thing
        // accumulating is the per-IP failure counter. Threshold is 10.
        for (int i = 0; i < 10; i++) {
            loginExpect("ghost" + i, "wrongpassword!", ip, 401);
        }

        // The 11th attempt from this IP is throttled with 429, regardless of
        // which username it targets and independent of any account's lock state.
        loginExpect("ghost-final", "wrongpassword!", ip, 429);

        // A DIFFERENT IP is unaffected: the throttle is per-IP, not global.
        loginExpect("bob", "wrongpassword!", "9.9.9.9", 401);
    }
}
