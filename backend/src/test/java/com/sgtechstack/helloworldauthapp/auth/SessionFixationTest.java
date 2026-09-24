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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The session id must change when a caller authenticates.
 *
 * <h2>The attack this prevents</h2>
 *
 * Session fixation works by getting a victim to use a session id the attacker
 * already knows — planted through a link, a subdomain-scoped cookie, or any
 * injection that can set one. The victim then logs in. If the id survives
 * authentication, the attacker's pre-known id is now an authenticated session,
 * and they never needed the password.
 *
 * <p>Rotating the id on login breaks it: whatever the attacker planted is
 * discarded at the moment it would have become valuable.
 *
 * <h2>Why this needed a test</h2>
 *
 * The protection is Spring Security's default, which is exactly why it was
 * unasserted — and exactly why it is worth asserting. A default is a decision
 * somebody else made, held in place by nothing in this repository. Setting
 * {@code sessionFixation().none()}, or replacing the session-management block
 * while chasing an unrelated problem, would remove it silently. The {@code
 * sessionManagement} DSL here is already configured for something else
 * (registering the {@code SessionRegistry} so sessions can be revoked), so it is
 * a block that gets edited.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SessionFixationTest {

    private static final String USERNAME = "fixation-user";
    private static final String PASSWORD = "fixation-password-1234";

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
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(new User(USERNAME, "fixation@example.com",
                passwordEncoder.encode(PASSWORD), Role.USER, true));
    }

    @Test
    void theSessionIdChangesAcrossSuccessfulAuthentication() throws Exception {
        // Stands in for the id an attacker planted: a session that exists before
        // the victim authenticates.
        MockHttpSession preAuthSession = new MockHttpSession();
        String preAuthId = preAuthSession.getId();

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .session(preAuthSession)
                        .with(csrf())
                        .param("username", USERNAME)
                        .param("password", PASSWORD))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession authenticated = (MockHttpSession) login.getRequest().getSession(false);

        assertThat(authenticated).isNotNull();
        assertThat(authenticated.getId())
                .as("a session id that survives authentication makes a planted id an authenticated one")
                .isNotEqualTo(preAuthId);
    }

    @Test
    void theRotatedSessionIsTheOneThatWorks() throws Exception {
        MockHttpSession preAuthSession = new MockHttpSession();

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .session(preAuthSession)
                        .with(csrf())
                        .param("username", USERNAME)
                        .param("password", PASSWORD))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession authenticated = (MockHttpSession) login.getRequest().getSession(false);

        // The id changing is only half the property. What matters is that the
        // authenticated context travelled with the new id — a rotation that
        // dropped the authentication would also pass the assertion above while
        // breaking login entirely.
        mockMvc.perform(get("/api/hello").session(authenticated))
                .andExpect(status().isOk());
    }

    @Test
    void aFailedLoginDoesNotProduceAnAuthenticatedSession() throws Exception {
        MockHttpSession preAuthSession = new MockHttpSession();

        mockMvc.perform(post("/api/auth/login")
                        .session(preAuthSession)
                        .with(csrf())
                        .param("username", USERNAME)
                        .param("password", "wrong-password-entirely"))
                .andExpect(status().isUnauthorized());

        // The pre-auth session must not have been upgraded on the way past. This
        // is the same session object the attacker would hold.
        mockMvc.perform(get("/api/hello").session(preAuthSession))
                .andExpect(status().isUnauthorized());
    }
}
