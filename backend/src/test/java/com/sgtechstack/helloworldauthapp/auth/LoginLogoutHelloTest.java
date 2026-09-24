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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class LoginLogoutHelloTest {

    private static final String USERNAME = "loginuser";
    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    private MockMvc mockMvc;

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
        User user = new User(USERNAME, "loginuser@example.com", passwordEncoder.encode(PASSWORD), Role.USER, true);
        // Three failures that happened just now. The timestamp matters: the
        // counter only accumulates for failures inside LockoutPolicy's window,
        // so a count with no accompanying timestamp would represent a state the
        // application can no longer produce, and the next failure would
        // correctly start a fresh streak at 1 rather than continuing to 4.
        user.setFailedLoginAttempts(3);
        user.setLastFailedLoginAt(Instant.now());
        userRepository.save(user);
    }

    @Test
    void helloWithoutSessionIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/hello"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void successfulLoginSetsSessionCookieAndResetsFailedAttempts() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .param("username", USERNAME)
                        .param("password", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(USERNAME))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        User reloaded = userRepository.findByUsernameIgnoreCase(USERNAME).orElseThrow();
        assertThat(reloaded.getFailedLoginAttempts()).isZero();

        // The session actually authenticates subsequent requests, exactly
        // as the SESSION cookie carrying this same session ID would in a
        // real browser.
        mockMvc.perform(get("/api/hello").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello, " + USERNAME));
    }

    @Test
    void wrongPasswordReturnsGenericErrorAndIncrementsFailedAttempts() throws Exception {
        int before = userRepository.findByUsernameIgnoreCase(USERNAME).orElseThrow().getFailedLoginAttempts();

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .param("username", USERNAME)
                        .param("password", "wrong-password-entirely"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));

        int after = userRepository.findByUsernameIgnoreCase(USERNAME).orElseThrow().getFailedLoginAttempts();
        assertThat(after).isEqualTo(before + 1);
    }

    @Test
    void unknownUsernameReturnsIdenticalGenericErrorAsWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .param("username", "no-such-user-at-all")
                        .param("password", "whatever-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"))
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @Test
    void logoutInvalidatesSessionAndReplayedCookieIsRejected() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .param("username", USERNAME)
                        .param("password", PASSWORD))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        // Session works before logout.
        mockMvc.perform(get("/api/hello").session(session))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/logout").with(csrf()).session(session))
                .andExpect(status().isNoContent());

        // Replaying the same (now-invalidated) session must be rejected,
        // exactly as a replayed SESSION cookie would be against a real
        // servlet container after the server-side session is destroyed.
        mockMvc.perform(get("/api/hello").session(session))
                .andExpect(status().isUnauthorized());
    }
}
