package com.sgtechstack.helloworldauthapp.account;

import com.sgtechstack.helloworldauthapp.audit.AuditAction;
import com.sgtechstack.helloworldauthapp.audit.AuditEvent;
import com.sgtechstack.helloworldauthapp.audit.AuditEventRepository;
import com.sgtechstack.helloworldauthapp.auth.StepUpAuthenticator;
import com.sgtechstack.helloworldauthapp.passwordreset.PasswordResetToken;
import com.sgtechstack.helloworldauthapp.passwordreset.PasswordResetTokenRepository;
import com.sgtechstack.helloworldauthapp.user.Role;
import com.sgtechstack.helloworldauthapp.user.User;
import com.sgtechstack.helloworldauthapp.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Data-subject rights: a user can read everything held about them, and have it
 * erased.
 *
 * <h2>Why this had to be self-service</h2>
 *
 * Deletion was admin-only and export did not exist, so both rights were exercisable
 * only by asking somebody — a process, not a feature, and this repository has no
 * process. Routing an erasure request through an administrator also means the
 * request itself creates a record of who asked to be forgotten, held by the party
 * they are asking.
 *
 * <p>Self-service removes the identity question too: the requester holds the session
 * and re-proves the password, which is stronger evidence than any email-based
 * verification an admin workflow would use.
 *
 * <h2>The last-admin case</h2>
 *
 * The sole administrator erasing their own account is the one fully reachable route
 * to zero administrators, and the "not yourself" check offers nothing here because
 * targeting yourself is the point of the endpoint. This right and {@code
 * LastAdminGuard} shipped together for that reason — the right without the guard
 * would have introduced the hole the threat model warned about.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AccountSelfServiceTest {

    private static final String USERNAME = "selfservice-user";
    private static final String EMAIL = "selfservice@example.com";
    private static final String PASSWORD = "selfservice-password-1234";

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

    private UUID userId;

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();

        userId = userRepository.save(new User(USERNAME, EMAIL,
                passwordEncoder.encode(PASSWORD), Role.USER, true)).getId();
    }

    @Test
    void aUserCanExportEverythingHeldAboutThem() throws Exception {
        mockMvc.perform(get("/api/account/export").session(login(USERNAME)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(USERNAME))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.createdAt").exists())
                // The unglamorous operational columns are included deliberately. An
                // export that quietly omits a field makes a false claim, because the
                // reader takes it for the full picture.
                .andExpect(jsonPath("$.failedLoginAttempts").exists())
                .andExpect(jsonPath("$.lockedUntil").doesNotExist());
    }

    @Test
    void theExportNeverIncludesThePasswordHash() throws Exception {
        String body = mockMvc.perform(get("/api/account/export").session(login(USERNAME)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Not the user's data in any useful sense: it is a credential verifier, and
        // handing over a BCrypt hash gives an offline cracking target while telling
        // its owner nothing they do not already know.
        assertThat(body).doesNotContain("passwordHash");
        assertThat(body).doesNotContain("$2a$").doesNotContain("$2b$");
    }

    @Test
    void theExportReportsResetHistoryAsMetadataWithoutAnyTokenHash() throws Exception {
        User user = userRepository.findById(userId).orElseThrow();
        PasswordResetToken used = new PasswordResetToken(user, "a-secret-token-hash",
                Instant.now().plus(Duration.ofMinutes(30)));
        used.markUsed();
        tokenRepository.save(used);

        String body = mockMvc.perform(get("/api/account/export").session(login(USERNAME)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordResetRequests.length()").value(1))
                .andExpect(jsonPath("$.passwordResetRequests[0].usedAt").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // The history is what has meaning to the user; the hash is a secret verifier
        // and belongs in neither the export nor anywhere else the user can reach.
        assertThat(body).doesNotContain("a-secret-token-hash");
    }

    @Test
    void theExportEmbedsThePrivacyNoticeInForce() throws Exception {
        mockMvc.perform(get("/api/account/export").session(login(USERNAME)))
                .andExpect(status().isOk())
                // Embedded rather than linked, so the downloaded file states on its
                // face what the data is for and how long it is kept.
                .andExpect(jsonPath("$.notice.lawfulBasis").exists())
                .andExpect(jsonPath("$.notice.retention").isArray());
    }

    @Test
    void theExportIsSentAsAnUncachedDownload() throws Exception {
        mockMvc.perform(get("/api/account/export").session(login(USERNAME)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"my-account-data.json\""))
                // A rendered page of personal data sits in the back-forward cache and
                // the tab's history; a download goes where the user put it.
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
    }

    @Test
    void anExportIsAudited() throws Exception {
        mockMvc.perform(get("/api/account/export").session(login(USERNAME)))
                .andExpect(status().isOk());

        List<AuditEvent> exports = auditRepository.findAllByActionOrderByOccurredAtAsc(AuditAction.SELF_EXPORT);
        assertThat(exports).isNotEmpty();
        // That the subject authorised the read does not make a bulk read of personal
        // data uninteresting afterwards.
        assertThat(exports.get(exports.size() - 1).getActorId()).isEqualTo(userId);
    }

    @Test
    void anUnauthenticatedCallerCannotExportAnything() throws Exception {
        mockMvc.perform(get("/api/account/export"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aUserCanEraseTheirOwnAccountAndItsTokens() throws Exception {
        User user = userRepository.findById(userId).orElseThrow();
        tokenRepository.save(new PasswordResetToken(user, "token-to-be-removed",
                Instant.now().plus(Duration.ofMinutes(30))));

        MockHttpSession session = login(USERNAME);

        mockMvc.perform(delete("/api/account")
                        .session(session)
                        .with(csrf())
                        .header(StepUpAuthenticator.CONFIRM_PASSWORD_HEADER, PASSWORD))
                .andExpect(status().isNoContent());

        // A real delete, not a flag. "Erasure" that leaves the data in place with a
        // boolean beside it is the thing the right exists to prevent.
        assertThat(userRepository.findById(userId)).isEmpty();
        assertThat(tokenRepository.findAll()).isEmpty();
    }

    @Test
    void erasureRevokesTheSessionThatRequestedIt() throws Exception {
        MockHttpSession session = login(USERNAME);

        mockMvc.perform(delete("/api/account")
                        .session(session)
                        .with(csrf())
                        .header(StepUpAuthenticator.CONFIRM_PASSWORD_HEADER, PASSWORD))
                .andExpect(status().isNoContent());

        // Otherwise the session stays authenticated against a principal that no
        // longer exists — the same defect the admin delete path had.
        mockMvc.perform(get("/api/hello").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void erasureWithoutTheConfirmedPasswordIsRefused() throws Exception {
        mockMvc.perform(delete("/api/account")
                        .session(login(USERNAME))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Re-enter your password to confirm this action"));

        assertThat(userRepository.findById(userId)).isPresent();
    }

    @Test
    void erasureWithAWrongPasswordIsRefused() throws Exception {
        mockMvc.perform(delete("/api/account")
                        .session(login(USERNAME))
                        .with(csrf())
                        .header(StepUpAuthenticator.CONFIRM_PASSWORD_HEADER, "not-my-password"))
                .andExpect(status().isForbidden());

        assertThat(userRepository.findById(userId)).isPresent();
    }

    @Test
    void anErasureIsAuditedAndTheRecordOutlivesTheAccount() throws Exception {
        mockMvc.perform(delete("/api/account")
                        .session(login(USERNAME))
                        .with(csrf())
                        .header(StepUpAuthenticator.CONFIRM_PASSWORD_HEADER, PASSWORD))
                .andExpect(status().isNoContent());

        List<AuditEvent> erasures = auditRepository.findAllByActionOrderByOccurredAtAsc(AuditAction.SELF_ERASURE);
        assertThat(erasures).isNotEmpty();

        // The record holds a pseudonymous reference, so it demonstrates that an
        // erasure happened without being a surviving copy of who was erased.
        AuditEvent latest = erasures.get(erasures.size() - 1);
        assertThat(latest.getActorRef()).doesNotContain(USERNAME);
        assertThat(userRepository.findById(userId)).isEmpty();
    }

    @Test
    void theSoleAdminCannotEraseThemselvesAndStrandTheSystem() throws Exception {
        // The one fully reachable route to zero administrators, and the reason this
        // right shipped alongside LastAdminGuard rather than after it.
        userRepository.deleteAll();
        userRepository.save(new User("only-admin", "only-admin@example.com",
                passwordEncoder.encode(PASSWORD), Role.ADMIN, true));

        mockMvc.perform(delete("/api/account")
                        .session(login("only-admin"))
                        .with(csrf())
                        .header(StepUpAuthenticator.CONFIRM_PASSWORD_HEADER, PASSWORD))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("last enabled admin")));

        assertThat(userRepository.countByRoleAndEnabledTrue(Role.ADMIN)).isEqualTo(1);
    }

    @Test
    void anAdminMayEraseThemselvesOnceAnotherAdminRemains() throws Exception {
        // The control case: the guard is about the count, not a blanket rule that
        // admins cannot exercise their own rights.
        userRepository.deleteAll();
        userRepository.save(new User("admin-one", "admin-one@example.com",
                passwordEncoder.encode(PASSWORD), Role.ADMIN, true));
        userRepository.save(new User("admin-two", "admin-two@example.com",
                passwordEncoder.encode(PASSWORD), Role.ADMIN, true));

        mockMvc.perform(delete("/api/account")
                        .session(login("admin-one"))
                        .with(csrf())
                        .header(StepUpAuthenticator.CONFIRM_PASSWORD_HEADER, PASSWORD))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findByUsernameIgnoreCase("admin-one")).isEmpty();
        assertThat(userRepository.countByRoleAndEnabledTrue(Role.ADMIN)).isEqualTo(1);
    }

    @Test
    void erasureWithoutTheCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(delete("/api/account")
                        .session(login(USERNAME))
                        .header(StepUpAuthenticator.CONFIRM_PASSWORD_HEADER, PASSWORD))
                .andExpect(status().isForbidden());

        assertThat(userRepository.findById(userId)).isPresent();
    }

    private MockHttpSession login(String username) throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .param("username", username)
                        .param("password", PASSWORD))
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession(false);
    }
}
