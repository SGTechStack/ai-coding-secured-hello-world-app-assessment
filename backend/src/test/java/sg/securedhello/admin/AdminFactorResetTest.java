package sg.securedhello.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.sql.Timestamp;
import java.time.Duration;
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
import sg.securedhello.mfa.TotpWindow;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SessionRows;
import sg.securedhello.testsupport.SignedIn;
import sg.securedhello.testsupport.TotpFactors;

import tools.jackson.databind.json.JsonMapper;

/**
 * {@code DELETE /api/admin/users/{uuid}/totp} on the shared context (ADR-024; ADR-049). The reset is exempt from the
 * two-admin count, so the enrolled admins this shared database accumulates never decide a result here;
 * {@link AdminFactorResetInvariantTest} pins the count on a database of its own.
 */
class AdminFactorResetTest extends CtxDefaultTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String REMOVED_ROW = "TOTP factor reset.";
    private static final String NEW_PASSWORD = "amber meadow softly turns";

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

    private ResultActions reset(CsrfSession session, UUID subject) throws Exception {
        return mockMvc.perform(delete("/api/admin/users/" + subject + "/totp").with(session.inHeader()));
    }

    private int rows(String table, UUID userId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE user_id = ?", Integer.class,
                userId);
        return count == null ? 0 : count;
    }

    /** A pending enrolment for {@code account}, as a half-finished provisioning leaves it. */
    private void pendingEnrolment(Account account) {
        jdbc.update("INSERT INTO pending_totp (user_id, totp_key, key_version, created_at) VALUES (?, ?, ?, ?)",
                account.id(), cipher.seal(account.id(), new byte[TotpSecretCipher.SECRET_BYTES]),
                cipher.keyVersion(), Timestamp.from(clock.instant()));
    }

    @Test
    @Proves("T-MFA-009")
    void aResetDeletesTheConfirmedAndPendingRowsAndOneRowNamesActorAndSubject() throws Exception {
        Account actor = accounts.withRole("ADMIN");
        Account subject = accounts.withRole("ADMIN");
        factors.enrol(subject);
        pendingEnrolment(subject);
        CsrfSession session = verifiedAdmin(actor);

        try (AuditCapture audit = AuditCapture.start()) {
            String body = reset(session, subject.id()).andExpect(status().isNoContent())
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).isEmpty();
            assertThat(audit.withMessage(REMOVED_ROW)).singleElement().satisfies(row -> assertThat(row)
                    .containsEntry("event.action", "totp-remove")
                    .containsEntry("event.outcome", "success")
                    .containsEntry("user.id", actor.id().toString())
                    .containsEntry("user.target.id", subject.id().toString()));
        }

        assertThat(rows("totp_user_details", subject.id())).isZero();
        assertThat(rows("pending_totp", subject.id())).isZero();
        assertThat(rows("totp_user_details", actor.id())).as("the actor's factor stays").isOne();
    }

    @Test
    void resettingYourOwnFactorIsRefusedAndChangesNothing() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        CsrfSession session = verifiedAdmin(admin);

        try (AuditCapture audit = AuditCapture.start()) {
            String body = reset(session, admin.id()).andExpect(problem(ErrorCode.ACCESS_DENIED))
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain("totpSecret", "totpKey", "passwordHash");
            assertThat(audit.withMessage("Administrative action refused.")).singleElement().satisfies(row ->
                    assertThat(row).containsEntry("event.reason", "SELF_ACTION")
                            .containsEntry("user.target.id", admin.id().toString()));
            assertThat(audit.withMessage(REMOVED_ROW)).isEmpty();
        }

        assertThat(rows("totp_user_details", admin.id())).isOne();
        mockMvc.perform(get("/api/admin/users").cookie(session.cookie())).andExpect(status().isOk());
    }

    @Test
    void anUnknownAccountIsDenied() throws Exception {
        CsrfSession session = verifiedAdmin(accounts.withRole("ADMIN"));

        reset(session, UUID.randomUUID()).andExpect(problem(ErrorCode.ACCESS_DENIED));
    }

    /** ADR-049: the subject's sessions end, and at their next sign-in they are sent to enrolment and re-enrol unaided. */
    @Test
    void afterAResetTheSubjectsSessionsAreGoneAndTheyReEnrolAtTheirNextSignIn() throws Exception {
        Account subject = accounts.withRole("ADMIN");
        CsrfSession live = verifiedAdmin(subject);
        CsrfSession session = verifiedAdmin(accounts.withRole("ADMIN"));

        reset(session, subject.id()).andExpect(status().isNoContent());

        assertThat(new SessionRows(jdbc).exists(SessionRows.idOf(live.cookie().getValue()))).isFalse();
        mockMvc.perform(get("/api/profile").cookie(live.cookie())).andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
        assertReEnrolsAtNextSignIn(subject);
    }

    /**
     * A tier-2 disable is cleared in-app only by a factor reset (ADR-049; ADR-027). The trip also forced a password
     * change, which the subject completes first, since enrolment is off the forced-change allowlist.
     */
    @Test
    void aTierTwoDisabledAdminIsRecoveredByAReset() throws Exception {
        Account subject = accounts.withRole("ADMIN");
        byte[] secret = factors.enrol(subject);
        // One failure below the tier-2 cap of 100, then the failure that trips it through the real route.
        jdbc.update("UPDATE totp_user_details SET cumulative_failures = 99 WHERE user_id = ?", subject.id());
        TotpFactors.verify(mockMvc, SignedIn.as(mockMvc, subject), factors.wrongCode(secret))
                .andExpect(problem(ErrorCode.FACTOR_DISABLED));
        CsrfSession session = verifiedAdmin(accounts.withRole("ADMIN"));

        reset(session, subject.id()).andExpect(status().isNoContent());

        CsrfSession forced = SignedIn.as(mockMvc, subject);
        mockMvc.perform(patch("/api/profile/password").with(forced.inHeader()).contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("currentPassword", subject.password(),
                                "newPassword", NEW_PASSWORD))))
                .andExpect(status().isNoContent());
        assertReEnrolsAtNextSignIn(new Account(subject.id(), subject.username(), NEW_PASSWORD));
    }

    /**
     * Signs {@code subject} in with the password alone: the self-read shows no factor and no rebind, the admin surface
     * answers 422 {@code FACTOR_ENROLMENT_REQUIRED}, and enrolment then verification open it again.
     */
    private void assertReEnrolsAtNextSignIn(Account subject) throws Exception {
        CsrfSession signedIn = SignedIn.as(mockMvc, subject);
        mockMvc.perform(get("/api/profile").cookie(signedIn.cookie())).andExpect(status().isOk())
                .andExpect(jsonPath("$.factors.enrolled").value(false))
                .andExpect(jsonPath("$.factors.rebindRequired").value(false));
        mockMvc.perform(get("/api/admin/users").cookie(signedIn.cookie()))
                .andExpect(problem(ErrorCode.FACTOR_ENROLMENT_REQUIRED));

        byte[] secret = factors.enrolThroughRoutes(mockMvc, signedIn);
        clock.advance(Duration.ofSeconds(TotpWindow.STEP_SECONDS));

        CsrfSession verified = factors.verified(mockMvc, SignedIn.as(mockMvc, subject), secret);
        mockMvc.perform(get("/api/admin/users").cookie(verified.cookie())).andExpect(status().isOk());
    }
}
