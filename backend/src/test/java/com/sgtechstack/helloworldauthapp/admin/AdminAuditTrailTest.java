package com.sgtechstack.helloworldauthapp.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgtechstack.helloworldauthapp.audit.AuditAction;
import com.sgtechstack.helloworldauthapp.audit.AuditEvent;
import com.sgtechstack.helloworldauthapp.audit.AuditEventRepository;
import com.sgtechstack.helloworldauthapp.auth.StepUpAuthenticator;
import com.sgtechstack.helloworldauthapp.logging.UserPseudonym;
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

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Irreversible admin actions must leave a durable record, not just a log line.
 *
 * <h2>Why a log line was not enough</h2>
 *
 * Each destructive mutation used to emit one unstructured INFO line: no client
 * address, no correlation id, no store that outlives log rotation, and — the part
 * that matters most — written outside the transaction. The mutation could commit
 * and the line be lost, or the line be written and the transaction roll back, with
 * no way afterwards to tell which had happened. A record that can disagree with
 * the thing it describes is not evidence.
 *
 * <p>The row now shares the mutation's transaction, so the two commit together or
 * not at all. The log line is still emitted, but as a mirror for the aggregator
 * rather than as the record itself.
 *
 * <h2>The deletion case is the one that drove the design</h2>
 *
 * {@code AuditEvent} holds no foreign key to {@code users}. A foreign key would
 * force the audit row to cascade away with the account or block its deletion; the
 * record has to outlive its subject, which is asserted below.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AdminAuditTrailTest {

    private static final String ADMIN = "audit-admin";
    private static final String SECOND_ADMIN = "audit-admin-two";
    private static final String TARGET = "audit-target";
    private static final String PASSWORD = "audit-password-1234";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private AuditEventRepository auditRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserPseudonym pseudonym;

    private UUID adminId;
    private UUID targetId;

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();

        adminId = userRepository.save(new User(ADMIN, "audit-admin@example.com",
                passwordEncoder.encode(PASSWORD), Role.ADMIN, true)).getId();
        // A second admin, so the last-admin guard does not refuse the mutations
        // under test here.
        userRepository.save(new User(SECOND_ADMIN, "audit-admin-two@example.com",
                passwordEncoder.encode(PASSWORD), Role.ADMIN, true));
        targetId = userRepository.save(new User(TARGET, "audit-target@example.com",
                passwordEncoder.encode(PASSWORD), Role.USER, true)).getId();
    }

    @Test
    void disablingAnAccountWritesAnAuditRow() throws Exception {
        long before = auditRepository.count();

        mockMvc.perform(patch("/api/admin/users/{id}/enabled", targetId)
                        .session(login(ADMIN))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetEnabledRequest(false))))
                .andExpect(status().isOk());

        assertThat(auditRepository.count()).isEqualTo(before + 1);

        AuditEvent event = latestFor(AuditAction.SET_ENABLED);
        assertThat(event.getActorId()).isEqualTo(adminId);
        assertThat(event.getTargetId()).isEqualTo(targetId);
        assertThat(event.getDetail()).contains("enabled=false");
    }

    @Test
    void aRoleChangeRecordsBothTheOldAndTheNewRole() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{id}/role", targetId)
                        .session(login(ADMIN))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RoleChangeRequest(Role.ADMIN))))
                .andExpect(status().isOk());

        // "became an admin" is much less useful than "was a USER and became an
        // ADMIN": only the second distinguishes a promotion from a no-op re-apply.
        assertThat(latestFor(AuditAction.CHANGE_ROLE).getDetail()).contains("USER -> ADMIN");
    }

    @Test
    void theAuditRowSurvivesTheAccountItDescribes() throws Exception {
        mockMvc.perform(delete("/api/admin/users/{id}", targetId)
                        .session(login(ADMIN))
                        .with(csrf())
                        .header(StepUpAuthenticator.CONFIRM_PASSWORD_HEADER, PASSWORD))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(targetId)).isEmpty();

        AuditEvent event = latestFor(AuditAction.DELETE_USER);
        // The whole reason actor_id and target_id are plain UUID columns rather
        // than associations. With a foreign key this row would have been cascaded
        // away with the account, or the delete would have failed.
        assertThat(event.getTargetId()).isEqualTo(targetId);
        assertThat(event.getTargetRef()).isEqualTo(pseudonym.of(TARGET));
    }

    @Test
    void theRowCarriesTheClientAddressAndACorrelationId() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{id}/enabled", targetId)
                        .session(login(ADMIN))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetEnabledRequest(false))))
                .andExpect(status().isOk());

        AuditEvent event = latestFor(AuditAction.SET_ENABLED);

        // Neither was recorded before. Without the address there is no way to tell
        // an action taken from the office from one taken from elsewhere; without the
        // correlation id the row cannot be joined to the log lines around it.
        assertThat(event.getClientIp()).isNotBlank().isNotEqualTo("none");
        assertThat(event.getRequestId()).isNotBlank().isNotEqualTo("none");
    }

    @Test
    void identitiesAreRecordedAsPseudonymousReferencesNotUsernames() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{id}/enabled", targetId)
                        .session(login(ADMIN))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetEnabledRequest(false))))
                .andExpect(status().isOk());

        AuditEvent event = latestFor(AuditAction.SET_ENABLED);

        // The row stays useful for "same account as that other row" without the
        // audit table becoming a second permanent copy of who-is-who.
        assertThat(event.getActorRef()).isEqualTo(pseudonym.of(ADMIN)).doesNotContain(ADMIN);
        assertThat(event.getTargetRef()).isEqualTo(pseudonym.of(TARGET)).doesNotContain(TARGET);
    }

    @Test
    void aRefusedMutationWritesNoAuditRow() throws Exception {
        long before = auditRepository.count();

        // Self-targeting is rejected before anything is recorded. An audit trail
        // that logs attempts as though they were actions is one that cannot be
        // read at face value.
        mockMvc.perform(patch("/api/admin/users/{id}/enabled", adminId)
                        .session(login(ADMIN))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetEnabledRequest(false))))
                .andExpect(status().isBadRequest());

        assertThat(auditRepository.count()).isEqualTo(before);
    }

    @Test
    void theRepositoryExposesNoWayToAlterOrRemoveARecord() {
        // Structural, not behavioural. AuditEventRepository extends the bare
        // Repository marker rather than JpaRepository precisely so that delete,
        // deleteAll and deleteById are not inherited — on an append-only log those
        // are the operations it exists to prevent, and inheriting them would mean
        // the property held only until somebody typed one and got a green build.
        List<String> methods = java.util.Arrays.stream(AuditEventRepository.class.getMethods())
                .map(Method::getName)
                .toList();

        assertThat(methods).contains("save");
        assertThat(methods)
                .as("an append-only log must not expose mutation or deletion")
                .noneMatch(name -> name.startsWith("delete"))
                .noneMatch(name -> name.startsWith("saveAll"));
    }

    @Test
    void theEntityExposesNoSetters() {
        // The other half of append-only: even holding a managed AuditEvent, there
        // is no way to change what it says. Combined with
        // @Column(updatable = false), Hibernate will not emit an UPDATE either.
        assertThat(java.util.Arrays.stream(AuditEvent.class.getDeclaredMethods())
                .map(Method::getName)
                .filter(name -> name.startsWith("set"))
                .toList())
                .isEmpty();
    }

    private AuditEvent latestFor(AuditAction action) {
        List<AuditEvent> events = auditRepository.findAllByActionOrderByOccurredAtAsc(action);
        assertThat(events).as("no audit row for %s", action).isNotEmpty();
        return events.get(events.size() - 1);
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
