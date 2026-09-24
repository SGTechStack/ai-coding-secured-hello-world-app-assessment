package com.sgtechstack.helloworldauthapp.admin;

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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Deleting an account requires the acting admin to re-enter their password.
 *
 * <h2>What the session does and does not prove</h2>
 *
 * A valid session proves somebody authenticated as this account within the last
 * eight hours. It does not prove the person issuing <em>this</em> request is that
 * somebody: an unlocked laptop, a shared browser profile or a lifted cookie all
 * produce a perfectly valid session held by the wrong person.
 *
 * <p>For most requests that gap is the design, and re-prompting constantly would
 * train people to type their password into anything that asks. For the one action
 * with no undo it is not acceptable, so the window narrows from "the session
 * lifetime" to "this request" — the difference between an attacker needing a
 * cookie and needing the credential.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class StepUpAuthenticationTest {

    private static final String ADMIN = "stepup-admin";
    private static final String SECOND_ADMIN = "stepup-admin-two";
    private static final String TARGET = "stepup-target";
    private static final String PASSWORD = "stepup-password-1234";

    @Autowired
    private MockMvc mockMvc;

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

        userRepository.save(new User(ADMIN, "stepup-admin@example.com",
                passwordEncoder.encode(PASSWORD), Role.ADMIN, true));
        userRepository.save(new User(SECOND_ADMIN, "stepup-admin-two@example.com",
                passwordEncoder.encode(PASSWORD), Role.ADMIN, true));
        targetId = userRepository.save(new User(TARGET, "stepup-target@example.com",
                passwordEncoder.encode(PASSWORD), Role.USER, true)).getId();
    }

    @Test
    void deleteWithoutTheConfirmationHeaderIsRefused() throws Exception {
        mockMvc.perform(delete("/api/admin/users/{id}", targetId)
                        .session(login())
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Re-enter your password to confirm this action"));

        assertThat(userRepository.findById(targetId)).isPresent();
    }

    @Test
    void deleteWithAWrongPasswordIsRefusedIdenticallyToAMissingOne() throws Exception {
        // Identical response on purpose: a caller must not be able to tell from the
        // answer whether confirmation is expected, only that this request was not
        // authorized.
        mockMvc.perform(delete("/api/admin/users/{id}", targetId)
                        .session(login())
                        .with(csrf())
                        .header(StepUpAuthenticator.CONFIRM_PASSWORD_HEADER, "not-the-password"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Re-enter your password to confirm this action"));

        assertThat(userRepository.findById(targetId)).isPresent();
    }

    @Test
    void deleteWithABlankConfirmationIsRefused() throws Exception {
        mockMvc.perform(delete("/api/admin/users/{id}", targetId)
                        .session(login())
                        .with(csrf())
                        .header(StepUpAuthenticator.CONFIRM_PASSWORD_HEADER, "   "))
                .andExpect(status().isForbidden());

        assertThat(userRepository.findById(targetId)).isPresent();
    }

    @Test
    void theTargetsOwnPasswordDoesNotSatisfyTheConfirmation() throws Exception {
        // All three accounts here share a password, so this test needs the target's
        // to differ or it would pass for the wrong reason.
        User target = userRepository.findById(targetId).orElseThrow();
        target.setPasswordHash(passwordEncoder.encode("a-completely-different-password"));
        userRepository.save(target);

        mockMvc.perform(delete("/api/admin/users/{id}", targetId)
                        .session(login())
                        .with(csrf())
                        .header(StepUpAuthenticator.CONFIRM_PASSWORD_HEADER, "a-completely-different-password"))
                .andExpect(status().isForbidden());

        assertThat(userRepository.findById(targetId)).isPresent();
    }

    @Test
    void refusalIsForbiddenRatherThanUnauthorizedSoTheSessionSurvives() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(delete("/api/admin/users/{id}", targetId)
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        // A 401 would tell the browser the session was gone and send the SPA back
        // to the login screen, discarding the admin's context over a missing
        // confirmation on one action. The session is valid; this request was not.
        mockMvc.perform(delete("/api/admin/users/{id}", targetId)
                        .session(session)
                        .with(csrf())
                        .header(StepUpAuthenticator.CONFIRM_PASSWORD_HEADER, PASSWORD))
                .andExpect(status().isNoContent());
    }

    @Test
    void aPasswordChangedMidSessionIsTheOneThatCounts() throws Exception {
        MockHttpSession session = login();

        // The hash is re-read from the database rather than taken from the cached
        // principal, so a session that outlived a password change cannot confirm
        // with the password it was established under.
        User admin = userRepository.findByUsernameIgnoreCase(ADMIN).orElseThrow();
        admin.setPasswordHash(passwordEncoder.encode("rotated-password-5678"));
        userRepository.save(admin);

        mockMvc.perform(delete("/api/admin/users/{id}", targetId)
                        .session(session)
                        .with(csrf())
                        .header(StepUpAuthenticator.CONFIRM_PASSWORD_HEADER, PASSWORD))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/admin/users/{id}", targetId)
                        .session(session)
                        .with(csrf())
                        .header(StepUpAuthenticator.CONFIRM_PASSWORD_HEADER, "rotated-password-5678"))
                .andExpect(status().isNoContent());
    }

    @Test
    void nonDestructiveMutationsDoNotRequireConfirmation() throws Exception {
        // Step-up is scoped to the irreversible action. Requiring it everywhere
        // would make the prompt routine, and a routine password prompt is one
        // people type into anything that shows it.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/admin/users/{id}/enabled", targetId)
                        .session(login())
                        .with(csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk());
    }

    private MockHttpSession login() throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .param("username", ADMIN)
                        .param("password", PASSWORD))
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession(false);
    }
}
