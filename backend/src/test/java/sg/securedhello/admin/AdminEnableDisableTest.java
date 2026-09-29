package sg.securedhello.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.mfa.TotpSecretCipher;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SessionRows;
import sg.securedhello.testsupport.SignedIn;
import sg.securedhello.testsupport.TotpFactors;

/**
 * {@code PUT /api/admin/users/{uuid}/enabled} on the shared context (PRD Story 9; ADR-046; ADR-048). Every target here
 * is a {@code USER} or the actor, so the two-admin count, which this shared database cannot pin, never decides a
 * result; {@link TwoAdminInvariantTest} owns a database for that.
 */
class AdminEnableDisableTest extends CtxDefaultTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TotpSecretCipher cipher;

    private Accounts accounts;
    private TotpFactors factors;

    @BeforeEach
    void setUp() {
        accounts = new Accounts(jdbc, passwordEncoder);
        factors = new TotpFactors(jdbc, cipher, clock);
    }

    private CsrfSession verifiedAdmin(Account admin) throws Exception {
        return factors.verified(mockMvc, SignedIn.as(mockMvc, admin), factors.enrol(admin));
    }

    private ResultActions setEnabled(CsrfSession session, UUID id, String body) throws Exception {
        return mockMvc.perform(put("/api/admin/users/" + id + "/enabled").with(session.inHeader())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions setEnabled(CsrfSession session, UUID id, boolean enabled) throws Exception {
        return setEnabled(session, id, "{\"enabled\":" + enabled + "}");
    }

    private Map<String, Object> row(UUID id) {
        return jdbc.queryForMap(
                "SELECT enabled, force_password_change, credential_issued_at, password_hash FROM users WHERE id = ?",
                id);
    }

    @Test
    void disablingAUserEndsItsLiveSessionAndTheAuditRowNamesActorAndSubject() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        Account target = accounts.user();
        CsrfSession live = SignedIn.as(mockMvc, target);
        CsrfSession session = verifiedAdmin(admin);

        try (AuditCapture audit = AuditCapture.start()) {
            String body = setEnabled(session, target.id(), false).andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(target.id().toString()))
                    .andExpect(jsonPath("$.enabled").value(false))
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain("password", (String) row(target.id()).get("PASSWORD_HASH"));
            assertThat(audit.withMessage("Account disabled.")).singleElement().satisfies(entry ->
                    assertThat(entry).containsEntry("event.action", "user-administration")
                            .containsEntry("user.id", admin.id().toString())
                            .containsEntry("user.target.id", target.id().toString()));
        }

        assertThat(row(target.id())).containsEntry("ENABLED", false);
        assertThat(new SessionRows(jdbc).exists(SessionRows.idOf(live.cookie().getValue()))).isFalse();
        mockMvc.perform(get("/api/hello").cookie(live.cookie())).andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
        SignedIn.login(mockMvc, CsrfSession.bootstrap(mockMvc), target.username(), target.password())
                .andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
    }

    /** ADR-046: a re-enable is a forced-change issuance of the user's own prior password, confined to the allowlist. */
    @Test
    @Proves("T-ADM-005")
    void reEnablingIssuesAForcedChangeCredentialThatConfinesTheSessionToTheAllowlist() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        Account target = accounts.disabled();
        String hashBefore = (String) row(target.id()).get("PASSWORD_HASH");
        CsrfSession session = verifiedAdmin(admin);

        try (AuditCapture audit = AuditCapture.start()) {
            setEnabled(session, target.id(), true).andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(true));
            assertThat(audit.withMessage("Account enabled.")).singleElement().satisfies(entry ->
                    assertThat(entry).containsEntry("user.id", admin.id().toString())
                            .containsEntry("user.target.id", target.id().toString()));
        }

        Map<String, Object> after = row(target.id());
        assertThat(after).containsEntry("ENABLED", true).containsEntry("FORCE_PASSWORD_CHANGE", true)
                .containsEntry("PASSWORD_HASH", hashBefore);
        assertThat(((OffsetDateTime) after.get("CREDENTIAL_ISSUED_AT")).toInstant())
                .isCloseTo(clock.instant(), within(1, ChronoUnit.MICROS));

        CsrfSession reEnabled = SignedIn.as(mockMvc, target);
        mockMvc.perform(get("/api/profile").cookie(reEnabled.cookie())).andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordChangeRequired").value(true));
        mockMvc.perform(get("/api/hello").cookie(reEnabled.cookie()))
                .andExpect(problem(ErrorCode.PASSWORD_CHANGE_REQUIRED));
    }

    @Test
    void enablingAnAlreadyEnabledAccountIssuesNoCredential() throws Exception {
        Account target = accounts.user();
        CsrfSession session = verifiedAdmin(accounts.withRole("ADMIN"));

        setEnabled(session, target.id(), true).andExpect(status().isOk());

        assertThat(row(target.id())).containsEntry("FORCE_PASSWORD_CHANGE", false)
                .containsEntry("CREDENTIAL_ISSUED_AT", null);
    }

    @Test
    void anAdminDisablingOrEnablingThemselvesGetsAccessDeniedAndNothingChanges() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        CsrfSession session = verifiedAdmin(admin);

        for (boolean enabled : new boolean[] {false, true}) {
            try (AuditCapture audit = AuditCapture.start()) {
                setEnabled(session, admin.id(), enabled).andExpect(problem(ErrorCode.ACCESS_DENIED));
                assertThat(audit.withMessage("Administrative action refused.")).singleElement().satisfies(entry ->
                        assertThat(entry).containsEntry("event.reason", "SELF_ACTION")
                                .containsEntry("event.outcome", "failure")
                                .containsEntry("user.id", admin.id().toString())
                                .containsEntry("user.target.id", admin.id().toString()));
            }
        }
        assertThat(row(admin.id())).containsEntry("ENABLED", true).containsEntry("FORCE_PASSWORD_CHANGE", false);
        mockMvc.perform(get("/api/admin/users").cookie(session.cookie())).andExpect(status().isOk());
    }

    @Test
    void aFactorOlderThanTenMinutesIsExpiredForTheMutationWhileReadsStillSucceed() throws Exception {
        Account target = accounts.user();
        CsrfSession session = verifiedAdmin(accounts.withRole("ADMIN"));
        // Keep the session inside its idle window while the factor ages past 10 minutes.
        for (int step = 0; step < 2; step++) {
            clock.advance(Duration.ofMinutes(5).plusSeconds(30));
            mockMvc.perform(get("/api/admin/users/" + target.id()).cookie(session.cookie()))
                    .andExpect(status().isOk());
        }

        setEnabled(session, target.id(), false).andExpect(problem(ErrorCode.MISSING_FACTOR))
                .andExpect(jsonPath("$.factor").value("TOTP"))
                .andExpect(jsonPath("$.reason").value("EXPIRED"));
        mockMvc.perform(get("/api/admin/users").cookie(session.cookie())).andExpect(status().isOk());
        assertThat(row(target.id())).containsEntry("ENABLED", true);
    }

    @Test
    void anUnknownAccountIsDeniedAndABodyWithoutTheFlagIsInvalid() throws Exception {
        Account target = accounts.user();
        CsrfSession session = verifiedAdmin(accounts.withRole("ADMIN"));

        setEnabled(session, UUID.randomUUID(), false).andExpect(problem(ErrorCode.ACCESS_DENIED));
        setEnabled(session, target.id(), "{}").andExpect(problem(ErrorCode.VALIDATION_FAILED));
        setEnabled(session, target.id(), "{\"enabled\":\"no\"}").andExpect(problem(ErrorCode.VALIDATION_FAILED));
        assertThat(row(target.id())).containsEntry("ENABLED", true);
    }
}
