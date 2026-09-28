package com.example.helloauth;

import com.example.helloauth.domain.PasswordResetToken;
import com.example.helloauth.domain.Role;
import com.example.helloauth.domain.User;
import com.example.helloauth.repo.PasswordResetTokenRepository;
import com.example.helloauth.repo.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordResetTokenRepository tokenRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper mapper;

    private static final String PASSWORD = "CorrectHorse123";

    @BeforeEach
    void seedUser() {
        userRepository.findByUsername("alice").ifPresent(userRepository::delete);
        User u = new User();
        u.setUsername("alice");
        u.setEmail("alice@example.com");
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        u.setRole(Role.USER);
        u.setEnabled(true);
        userRepository.save(u);
    }

    // ---------- helpers ----------

    private MvcResult login(String username, String password, String ip, MockHttpSession session) throws Exception {
        MockHttpServletRequestBuilder req = post("/api/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
        if (ip != null) {
            req = req.header("X-Forwarded-For", ip);
        }
        if (session != null) {
            req = req.session(session);
        }
        return mvc.perform(req).andReturn();
    }

    /** Logs in and returns the authenticated session for reuse in follow-up requests. */
    private MockHttpSession loginForSession(String username, String password, String ip) throws Exception {
        MockHttpSession session = new MockHttpSession();
        MvcResult res = login(username, password, ip, session);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        return session;
    }

    // ---------- Story 2: login ----------

    @Test
    void loginSuccess() throws Exception {
        MvcResult res = login("alice", PASSWORD, "1.1.1.1", new MockHttpSession());
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = mapper.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("username").asText()).isEqualTo("alice");

        User reloaded = userRepository.findByUsername("alice").orElseThrow();
        assertThat(reloaded.getFailedLoginAttempts()).isZero();
        assertThat(reloaded.getLastLoginAt()).isNotNull();
    }

    @Test
    void loginWrongPasswordGenericError() throws Exception {
        MvcResult res = login("alice", "WrongPassword999", "2.2.2.2", null);
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
        assertThat(res.getResponse().getContentAsString()).contains("Invalid username or password");
    }

    @Test
    void loginUnknownUsernameIdenticalError() throws Exception {
        MvcResult wrong = login("alice", "WrongPassword999", "2.2.2.3", null);
        MvcResult unknown = login("ghost", "WrongPassword999", "2.2.2.4", null);
        assertThat(unknown.getResponse().getStatus()).isEqualTo(wrong.getResponse().getStatus());
        assertThat(unknown.getResponse().getContentAsString())
                .isEqualTo(wrong.getResponse().getContentAsString());
    }

    @Test
    void lockedAccountRejectedEvenWithCorrectPassword() throws Exception {
        User u = userRepository.findByUsername("alice").orElseThrow();
        u.setLockedUntil(Instant.now().plus(10, ChronoUnit.MINUTES));
        userRepository.save(u);

        MvcResult res = login("alice", PASSWORD, "3.3.3.3", null);
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
    }

    // ---------- Story 3: lockout & IP throttling ----------

    @Test
    void nFailuresTriggersLockout() throws Exception {
        // maxAttempts=3 in test config. Use distinct IPs to avoid IP throttle interfering.
        for (int i = 0; i < 3; i++) {
            login("alice", "WrongPassword999", "10.0.0." + i, null);
        }
        User locked = userRepository.findByUsername("alice").orElseThrow();
        assertThat(locked.isCurrentlyLocked()).isTrue();
        assertThat(locked.getFailedLoginAttempts()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void successAfterCooldownResetsCounter() throws Exception {
        User u = userRepository.findByUsername("alice").orElseThrow();
        // Simulate elapsed cooldown: had failures, lock already expired.
        u.setFailedLoginAttempts(3);
        u.setLockedUntil(Instant.now().minus(1, ChronoUnit.MINUTES));
        userRepository.save(u);

        MvcResult res = login("alice", PASSWORD, "4.4.4.4", null);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);

        User reloaded = userRepository.findByUsername("alice").orElseThrow();
        assertThat(reloaded.getFailedLoginAttempts()).isZero();
        assertThat(reloaded.getLockedUntil()).isNull();
    }

    @Test
    void ipThrottleEngagesIndependentlyOfAccountLockout() throws Exception {
        String ip = "9.9.9.9";
        // throttle max-per-ip=5. Fail 5 different usernames from the same IP.
        for (int i = 0; i < 5; i++) {
            login("nouser" + i, "WrongPassword999", ip, null);
        }
        // 6th attempt from the same IP (even for a valid user with correct password) is throttled.
        MvcResult res = login("alice", PASSWORD, ip, null);
        assertThat(res.getResponse().getStatus()).isEqualTo(429);

        // A legitimate user from a DIFFERENT IP is unaffected (not locked out by the attacker).
        MvcResult ok = login("alice", PASSWORD, "9.9.9.10", null);
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
    }

    // ---------- Story 4: logout ----------

    @Test
    void reusedSessionCookieRejectedAfterLogout() throws Exception {
        MockHttpSession authedSession = loginForSession("alice", PASSWORD, "5.5.5.5");

        // Authenticated request works before logout.
        mvc.perform(get("/api/hello").session(authedSession))
                .andExpect(status().isOk());

        // Logout.
        mvc.perform(post("/api/logout").with(csrf()).session(authedSession))
                .andExpect(status().isOk());

        // The same (now-invalidated) session is rejected.
        mvc.perform(get("/api/hello").session(authedSession))
                .andExpect(status().isUnauthorized());
    }

    // ---------- Story 5: protected greeting ----------

    @Test
    void helloRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/hello")).andExpect(status().isUnauthorized());
    }

    @Test
    void helloReturnsPersonalizedGreeting() throws Exception {
        MockHttpSession authed = loginForSession("alice", PASSWORD, "6.6.6.6");
        mvc.perform(get("/api/hello").session(authed))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Hello, alice")));
    }

    // ---------- Stories 6-7: password reset ----------

    @Test
    void resetRequestIsGenericRegardlessOfEmail() throws Exception {
        MvcResult known = mvc.perform(post("/api/password-reset/request")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@example.com\"}"))
                .andReturn();
        MvcResult unknown = mvc.perform(post("/api/password-reset/request")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\"}"))
                .andReturn();
        assertThat(known.getResponse().getStatus()).isEqualTo(200);
        assertThat(unknown.getResponse().getStatus()).isEqualTo(200);
        assertThat(unknown.getResponse().getContentAsString())
                .isEqualTo(known.getResponse().getContentAsString());
    }

    @Test
    void resetTokenSingleUseAndInvalidatesSessions() throws Exception {
        User alice = userRepository.findByUsername("alice").orElseThrow();

        // Establish an active session for alice.
        MockHttpSession authed = loginForSession("alice", PASSWORD, "7.7.7.7");
        mvc.perform(get("/api/hello").session(authed)).andExpect(status().isOk());

        // Insert a reset token (hash) directly, mirroring what the service stores.
        String plaintext = "known-reset-token-value";
        String hash = sha256(plaintext);
        tokenRepository.save(new PasswordResetToken(alice.getId(), hash,
                Instant.now().plus(20, ChronoUnit.MINUTES)));

        String newPassword = "BrandNewPass456";
        mvc.perform(post("/api/password-reset/confirm")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + plaintext + "\",\"password\":\"" + newPassword + "\"}"))
                .andExpect(status().isOk());

        // Password actually changed.
        User reloaded = userRepository.findByUsername("alice").orElseThrow();
        assertThat(passwordEncoder.matches(newPassword, reloaded.getPasswordHash())).isTrue();

        // Existing session invalidated.
        mvc.perform(get("/api/hello").session(authed)).andExpect(status().isUnauthorized());

        // Token is single-use: second confirm rejected.
        mvc.perform(post("/api/password-reset/confirm")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + plaintext + "\",\"password\":\"AnotherPass789\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void expiredResetTokenRejected() throws Exception {
        User alice = userRepository.findByUsername("alice").orElseThrow();
        String plaintext = "expired-token-value";
        tokenRepository.save(new PasswordResetToken(alice.getId(), sha256(plaintext),
                Instant.now().minus(1, ChronoUnit.MINUTES)));

        mvc.perform(post("/api/password-reset/confirm")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + plaintext + "\",\"password\":\"BrandNewPass456\"}"))
                .andExpect(status().isBadRequest());

        // Password unchanged.
        User reloaded = userRepository.findByUsername("alice").orElseThrow();
        assertThat(passwordEncoder.matches(PASSWORD, reloaded.getPasswordHash())).isTrue();
    }

    private String sha256(String value) {
        try {
            java.security.MessageDigest d = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.Base64.getEncoder().encodeToString(
                    d.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
