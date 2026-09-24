package com.sgtechstack.helloworldauthapp.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgtechstack.helloworldauthapp.auth.StepUpAuthenticator;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * An admin mutation that narrows what an account can do has to cut that
 * account's live sessions, not just update its row.
 *
 * <p>Writing the row alone is insufficient because authorities are resolved
 * once at login and then cached in the session. Before this was fixed, a
 * suspended user's existing session kept working and a demoted admin kept
 * admin authorities — long enough to re-promote themselves and undo the
 * demotion. The password-reset path already revoked sessions correctly; these
 * tests hold the admin path to the same standard.
 *
 * <p>Each test establishes a real session through the filter chain (so the
 * {@code SessionRegistry} is genuinely populated), proves it works, applies
 * the admin action, and then asserts the <em>same</em> session is rejected.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AdminSessionRevocationTest {

    private static final String ADMIN_USERNAME = "revocation-admin";
    private static final String ADMIN_PASSWORD = "admin-password-1234";
    private static final String TARGET_USERNAME = "revocation-target";
    private static final String TARGET_PASSWORD = "target-password-1234";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private UUID targetId;

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(new User(ADMIN_USERNAME, "revocation-admin@example.com",
                passwordEncoder.encode(ADMIN_PASSWORD), Role.ADMIN, true));
        User target = userRepository.save(new User(TARGET_USERNAME, "revocation-target@example.com",
                passwordEncoder.encode(TARGET_PASSWORD), Role.USER, true));
        targetId = target.getId();
    }

    @Test
    void disablingAUserRejectsTheirExistingSession() throws Exception {
        MockHttpSession targetSession = loginAndAssertUsable(TARGET_USERNAME, TARGET_PASSWORD);

        mockMvc.perform(patch("/api/admin/users/{id}/enabled", targetId)
                        .session(loginAndAssertUsable(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetEnabledRequest(false))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/hello").session(targetSession))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void demotingAnAdminRejectsTheirExistingSessionSoTheyCannotUndoIt() throws Exception {
        // Promote the target so there are two admins, then demote them again.
        User target = userRepository.findById(targetId).orElseThrow();
        target.setRole(Role.ADMIN);
        userRepository.save(target);

        MockHttpSession demotedSession = loginAndAssertUsable(TARGET_USERNAME, TARGET_PASSWORD);
        // Confirm the session really does carry admin authority before demotion,
        // otherwise the assertion below could pass for the wrong reason.
        mockMvc.perform(get("/api/admin/users").session(demotedSession))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/admin/users/{id}/role", targetId)
                        .session(loginAndAssertUsable(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RoleChangeRequest(Role.USER))))
                .andExpect(status().isOk());

        // The demoted admin must not be able to keep acting as one, which
        // includes not being able to re-promote themselves.
        mockMvc.perform(get("/api/admin/users").session(demotedSession))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deletingAUserRejectsTheirExistingSession() throws Exception {
        MockHttpSession targetSession = loginAndAssertUsable(TARGET_USERNAME, TARGET_PASSWORD);

        mockMvc.perform(delete("/api/admin/users/{id}", targetId)
                        .session(loginAndAssertUsable(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .with(csrf())
                        .header(StepUpAuthenticator.CONFIRM_PASSWORD_HEADER, ADMIN_PASSWORD))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/hello").session(targetSession))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void enablingAUserDoesNotDisturbOtherSessions() throws Exception {
        MockHttpSession adminSession = loginAndAssertUsable(ADMIN_USERNAME, ADMIN_PASSWORD);

        // Re-enabling an already-enabled account widens nothing, so it must not
        // be used as a roundabout way to log people out.
        mockMvc.perform(patch("/api/admin/users/{id}/enabled", targetId)
                        .session(adminSession)
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetEnabledRequest(true))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/users").session(adminSession))
                .andExpect(status().isOk());
    }

    /**
     * Logs in through the real filter chain and confirms the resulting session
     * is actually usable, so a later "rejected" assertion can only be
     * explained by the revocation under test.
     */
    private MockHttpSession loginAndAssertUsable(String username, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .param("username", username)
                        .param("password", password))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mockMvc.perform(get("/api/hello").session(session)).andExpect(status().isOk());

        return session;
    }
}
