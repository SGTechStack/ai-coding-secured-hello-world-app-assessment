package com.sgtechstack.helloworldauthapp.admin;

import com.sgtechstack.helloworldauthapp.audit.AuditAction;
import com.sgtechstack.helloworldauthapp.audit.AuditEvent;
import com.sgtechstack.helloworldauthapp.audit.AuditEventRepository;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The user listing must not hand over every registered email address.
 *
 * <h2>The finding</h2>
 *
 * A single {@code GET /api/admin/users} returned the full email of every account —
 * the entire personal-data holding of the system, to any admin, as the default
 * payload of the screen they open to change somebody's role. No minimisation, no
 * purpose limitation, no record that a read had happened. The PRD says the address
 * exists so a password reset can be delivered; nothing in user administration
 * needs it.
 *
 * <h2>Masking rather than removal, and why</h2>
 *
 * Dropping the field entirely was the first option and was rejected: the screen has
 * one genuine use for it, telling two similarly-named accounts apart before acting
 * irreversibly on the wrong one. Removing the only disambiguating field from a
 * screen whose actions cannot be undone trades a privacy problem for a safety one.
 *
 * <p>So the listing carries a masked form and the real address is reachable one
 * account at a time, with a stated purpose and an audit row.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class EmailMinimisationTest {

    private static final String ADMIN = "minimisation-admin";
    private static final String TARGET = "minimisation-target";
    private static final String TARGET_EMAIL = "samuel.wong@example.com";
    private static final String PASSWORD = "minimisation-password-1234";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private AuditEventRepository auditRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private UUID targetId;

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(new User(ADMIN, "minimisation-admin@example.com",
                passwordEncoder.encode(PASSWORD), Role.ADMIN, true));
        targetId = userRepository.save(new User(TARGET, TARGET_EMAIL,
                passwordEncoder.encode(PASSWORD), Role.USER, true)).getId();
    }

    @Test
    void theListingCarriesNoFullEmailAddress() throws Exception {
        mockMvc.perform(get("/api/admin/users").session(login()))
                .andExpect(status().isOk())
                // The field is gone from the contract, not merely blanked — a
                // present-but-empty field invites somebody to "fix" it later.
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[*].maskedEmail").exists());
    }

    @Test
    void theMaskedFormHidesTheLocalPartAndItsLength() throws Exception {
        mockMvc.perform(get("/api/admin/users").session(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username == '" + TARGET + "')].maskedEmail")
                        .value(org.hamcrest.Matchers.hasItem("s****@example.com")));
    }

    @Test
    void noResponseBodyFromTheListingContainsTheAddress() throws Exception {
        String body = mockMvc.perform(get("/api/admin/users").session(login()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Asserted against the whole payload rather than per-field, so a future
        // field that happens to carry the address is caught too.
        assertThat(body).doesNotContain(TARGET_EMAIL);
        assertThat(body).doesNotContain("samuel.wong");
    }

    @Test
    void theFullAddressIsAvailableOneAccountAtATimeWithAStatedPurpose() throws Exception {
        mockMvc.perform(get("/api/admin/users/{id}/email", targetId)
                        .param("purpose", "User asked us to check which address their reset link went to")
                        .session(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(TARGET_EMAIL))
                .andExpect(jsonPath("$.username").value(TARGET));
    }

    @Test
    void readingAnAddressWithoutStatingAPurposeIsRefused() throws Exception {
        mockMvc.perform(get("/api/admin/users/{id}/email", targetId).session(login()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("A purpose is required to read a user's email address"));
    }

    @Test
    void aBlankPurposeIsNotAPurpose() throws Exception {
        mockMvc.perform(get("/api/admin/users/{id}/email", targetId)
                        .param("purpose", "   ")
                        .session(login()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void readingAnAddressIsAudited() throws Exception {
        String purpose = "Support ticket 4711";

        mockMvc.perform(get("/api/admin/users/{id}/email", targetId)
                        .param("purpose", purpose)
                        .session(login()))
                .andExpect(status().isOk());

        List<AuditEvent> reads = auditRepository.findAllByActionOrderByOccurredAtAsc(AuditAction.READ_USER_EMAIL);
        assertThat(reads).isNotEmpty();

        AuditEvent latest = reads.get(reads.size() - 1);
        // Purpose limitation that leaves no trace is an assertion, not a control.
        // The point of the record is that a bulk harvest through this endpoint shows
        // up afterwards as a run of lookups with identical or thin justification.
        assertThat(latest.getDetail()).contains(purpose);
        assertThat(latest.getTargetId()).isEqualTo(targetId);
    }

    @Test
    void aRegularUserCannotReadAnybodysAddress() throws Exception {
        MockHttpSession userSession = (MockHttpSession) mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .param("username", TARGET)
                        .param("password", PASSWORD))
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession(false);

        // Including their own: the endpoint is admin tooling, and a user's own data
        // is reachable through the self-service export instead.
        mockMvc.perform(get("/api/admin/users/{id}/email", targetId)
                        .param("purpose", "curiosity")
                        .session(userSession))
                .andExpect(status().isForbidden());
    }

    @Test
    void anUnauthenticatedCallerCannotReadAnAddress() throws Exception {
        mockMvc.perform(get("/api/admin/users/{id}/email", targetId).param("purpose", "none"))
                .andExpect(status().isUnauthorized());
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
