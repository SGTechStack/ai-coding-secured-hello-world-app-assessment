package sg.securedhello.mfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

import org.apache.commons.codec.binary.Base32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import sg.securedhello.credential.CredentialTokenType;
import sg.securedhello.credential.CredentialTokens;
import sg.securedhello.error.ErrorCode;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.LogOutputGuard;
import sg.securedhello.testsupport.PasswordResets;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SignedIn;
import sg.securedhello.testsupport.TotpFactors;
import sg.securedhello.user.UserAccount;

import tools.jackson.databind.json.JsonMapper;

/**
 * The two-tier factor lockout over the full context (ADR-027; ADR-033): tier 1 locks and lifts on the shared clock,
 * tier 2 disables the factor, forces a password change and ends the sessions, and the disabled factor answers 423 on
 * every surface that could otherwise invite a challenge (R-MFA-006).
 */
class TotpLockoutTest extends CtxDefaultTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String FAILED_ROW = "TOTP verification failed.";
    private static final String LOCKED_ROW = "TOTP factor locked.";
    private static final String DISABLED_ROW = "TOTP factor disabled.";
    private static final String NEW_PASSWORD = "amber meadow softly turns";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TotpSecretCipher cipher;

    @Autowired
    private CredentialTokens tokens;

    private Accounts accounts;
    private TotpFactors factors;

    @BeforeEach
    void setUp() {
        accounts = new Accounts(jdbc, passwordEncoder);
        factors = new TotpFactors(jdbc, cipher, clock);
    }

    private Map<String, Object> factorRow(Account account) {
        return jdbc.queryForMap("SELECT failed_attempts, locked_until, cumulative_failures, factor_disabled_at, "
                + "last_used_counter FROM totp_user_details WHERE user_id = ?", account.id());
    }

    private Map<String, Object> accountRow(Account account) {
        return jdbc.queryForMap("SELECT force_password_change, credential_issued_at FROM users WHERE id = ?",
                account.id());
    }

    /** Sends {@code count} wrong codes on {@code session}, each refused as an invalid code. */
    private void failBelowTheLock(CsrfSession session, byte[] secret, int count) throws Exception {
        for (int i = 0; i < count; i++) {
            TotpFactors.verify(mockMvc, session, factors.wrongCode(secret))
                    .andExpect(problem(ErrorCode.INVALID_FACTOR));
        }
    }

    /**
     * Waits out the step just used, so the current code is not a replay, and verifies it. The session id rotates, so
     * {@code session} is spent.
     */
    private void verifyNextStep(CsrfSession session, byte[] secret) throws Exception {
        clock.advance(Duration.ofSeconds(TotpWindow.STEP_SECONDS));
        TotpFactors.verify(mockMvc, session, factors.code(secret)).andExpect(status().isNoContent());
    }

    /** Takes the factor to one failure below the tier-2 cap and sends the failure that trips it. */
    private MvcResult tripTier2(Account admin, byte[] secret, CsrfSession session) throws Exception {
        jdbc.update("UPDATE totp_user_details SET cumulative_failures = ? WHERE user_id = ?",
                TotpUserDetails.DISABLE_THRESHOLD - 1, admin.id());
        return TotpFactors.verify(mockMvc, session, factors.wrongCode(secret))
                .andExpect(problem(ErrorCode.FACTOR_DISABLED)).andReturn();
    }

    @Test
    @Proves({"T-MFA-020", "T-MFA-006"})
    void theTenthWrongCodeLocksForTwentyMinutesThenTheCodeVerifiesAndTheSuccessResetsTierOne() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        byte[] secret = factors.enrol(admin);
        CsrfSession session = SignedIn.as(mockMvc, admin);
        failBelowTheLock(session, secret, TotpUserDetails.LOCK_THRESHOLD - 1);

        try (AuditCapture audit = AuditCapture.start()) {
            TotpFactors.verify(mockMvc, session, factors.wrongCode(secret))
                    .andExpect(problem(ErrorCode.TOO_MANY_REQUESTS))
                    .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1200"))
                    .andExpect(jsonPath("$.factor").value("TOTP"))
                    .andExpect(jsonPath("$.reason").value("LOCKED"));
            assertThat(audit.withMessage(LOCKED_ROW)).singleElement().satisfies(row -> assertThat(row)
                    .containsEntry("user.id", admin.id().toString())
                    .containsEntry("log.level", "WARN"));
            assertThat(audit.withMessage(FAILED_ROW)).as("the locking failure is still a failure").hasSize(1);
        }

        clock.advance(TotpUserDetails.LOCK.minusSeconds(1));
        session = SignedIn.as(mockMvc, admin);
        try (AuditCapture audit = AuditCapture.start()) {
            TotpFactors.verify(mockMvc, session, factors.code(secret))
                    .andExpect(problem(ErrorCode.TOO_MANY_REQUESTS))
                    .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"))
                    .andExpect(jsonPath("$.factor").value("TOTP"));
            assertThat(audit.rows()).as("a refusal during the lock checks and counts nothing").isEmpty();
        }
        assertThat(factorRow(admin)).containsEntry("last_used_counter", null)
                .containsEntry("cumulative_failures", TotpUserDetails.LOCK_THRESHOLD);

        clock.advance(Duration.ofSeconds(1));
        TotpFactors.verify(mockMvc, session, factors.code(secret)).andExpect(status().isNoContent());
        assertThat(factorRow(admin)).containsEntry("failed_attempts", 0).containsEntry("locked_until", null)
                .as("a success never resets tier 2")
                .containsEntry("cumulative_failures", TotpUserDetails.LOCK_THRESHOLD);
    }

    @Test
    @Proves("T-MFA-020")
    void aSuccessResetsTheTierOneCountSoTheNextNineFailuresDoNotLock() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        byte[] secret = factors.enrol(admin);
        CsrfSession session = SignedIn.as(mockMvc, admin);
        failBelowTheLock(session, secret, TotpUserDetails.LOCK_THRESHOLD - 1);
        verifyNextStep(session, secret);

        failBelowTheLock(SignedIn.as(mockMvc, admin), secret, TotpUserDetails.LOCK_THRESHOLD - 1);

        assertThat(factorRow(admin)).containsEntry("failed_attempts", TotpUserDetails.LOCK_THRESHOLD - 1)
                .containsEntry("locked_until", null);
    }

    @Test
    void aFailureJustInsideTheWindowOfTheOneBeforeStillCountsTowardsTheLock() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        byte[] secret = factors.enrol(admin);
        failBelowTheLock(SignedIn.as(mockMvc, admin), secret, TotpUserDetails.LOCK_THRESHOLD - 1);
        clock.advance(TotpUserDetails.WINDOW.minusSeconds(1));

        TotpFactors.verify(mockMvc, SignedIn.as(mockMvc, admin), factors.wrongCode(secret))
                .andExpect(problem(ErrorCode.TOO_MANY_REQUESTS));
    }

    @Test
    void aFailureAWindowAfterTheOneBeforeRestartsTheTierOneCount() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        byte[] secret = factors.enrol(admin);
        failBelowTheLock(SignedIn.as(mockMvc, admin), secret, TotpUserDetails.LOCK_THRESHOLD - 1);
        clock.advance(TotpUserDetails.WINDOW);

        failBelowTheLock(SignedIn.as(mockMvc, admin), secret, 1);

        assertThat(factorRow(admin)).containsEntry("failed_attempts", 1).containsEntry("locked_until", null)
                .containsEntry("cumulative_failures", TotpUserDetails.LOCK_THRESHOLD);
    }

    @Test
    @Proves("T-MFA-015")
    void anEmptyCodeIsRefusedWithoutCountingOnEitherTier() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        factors.enrol(admin);
        CsrfSession session = SignedIn.as(mockMvc, admin);

        for (int i = 0; i < TotpUserDetails.LOCK_THRESHOLD + 1; i++) {
            TotpFactors.verify(mockMvc, session, "").andExpect(problem(ErrorCode.INVALID_FACTOR));
        }

        assertThat(factorRow(admin)).containsEntry("failed_attempts", 0).containsEntry("cumulative_failures", 0)
                .containsEntry("locked_until", null);
    }

    @Test
    @Proves("T-MFA-021")
    void oneHundredCumulativeFailuresInterleavedWithSuccessesAndATierOneLiftDisableTheFactor() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        byte[] secret = factors.enrol(admin);
        int perRound = TotpUserDetails.LOCK_THRESHOLD - 1;
        int rounds = 9;
        for (int round = 0; round < rounds; round++) {
            CsrfSession session = SignedIn.as(mockMvc, admin);
            failBelowTheLock(session, secret, perRound);
            verifyNextStep(session, secret);
        }
        CsrfSession session = SignedIn.as(mockMvc, admin);
        failBelowTheLock(session, secret, perRound);
        TotpFactors.verify(mockMvc, session, factors.wrongCode(secret))
                .andExpect(problem(ErrorCode.TOO_MANY_REQUESTS));
        clock.advance(TotpUserDetails.LOCK);
        session = SignedIn.as(mockMvc, admin);
        int left = TotpUserDetails.DISABLE_THRESHOLD - rounds * perRound - TotpUserDetails.LOCK_THRESHOLD;
        failBelowTheLock(session, secret, left - 1);
        assertThat(factorRow(admin)).containsEntry("factor_disabled_at", null);

        TotpFactors.verify(mockMvc, session, factors.wrongCode(secret)).andExpect(problem(ErrorCode.FACTOR_DISABLED));

        assertThat(factorRow(admin).get("factor_disabled_at")).isNotNull();
        assertThat(factorRow(admin)).containsEntry("cumulative_failures", TotpUserDetails.DISABLE_THRESHOLD);
        // Past the forced change the trip also set, the correct code is still refused: only rebinding clears tier 2.
        jdbc.update("UPDATE users SET force_password_change = FALSE WHERE id = ?", admin.id());
        clock.advance(Duration.ofSeconds(TotpWindow.STEP_SECONDS));
        TotpFactors.verify(mockMvc, SignedIn.as(mockMvc, admin), factors.code(secret))
                .andExpect(problem(ErrorCode.FACTOR_DISABLED));

        // An admin unlock clears tier 1 only: the factor stays disabled. A factor reset is what clears it.
        Account other = accounts.withRole("ADMIN");
        CsrfSession otherSession = factors.verified(mockMvc, SignedIn.as(mockMvc, other), factors.enrol(other));
        mockMvc.perform(post("/api/admin/users/" + admin.id() + "/unlock").with(otherSession.inHeader())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"USER_REQUEST\"}"))
                .andExpect(status().is2xxSuccessful());
        assertThat(factorRow(admin).get("factor_disabled_at")).as("after the unlock").isNotNull();
        clock.advance(Duration.ofSeconds(TotpWindow.STEP_SECONDS));
        TotpFactors.verify(mockMvc, SignedIn.as(mockMvc, admin), factors.code(secret))
                .andExpect(problem(ErrorCode.FACTOR_DISABLED));

        mockMvc.perform(delete("/api/admin/users/" + admin.id() + "/totp").with(otherSession.inHeader()))
                .andExpect(status().is2xxSuccessful());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM totp_user_details WHERE user_id = ?", Integer.class,
                admin.id())).as("the disabled factor is gone").isZero();
        mockMvc.perform(get("/api/profile").cookie(SignedIn.as(mockMvc, admin).cookie())).andExpect(status().isOk())
                .andExpect(jsonPath("$.factors.enrolled").value(false))
                .andExpect(jsonPath("$.factors.rebindRequired").value(false));
    }

    @Test
    void theTripForcesAPasswordChangeWithoutAnIssueTimeEndsTheSessionAndWritesTheErrorRow() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        byte[] secret = factors.enrol(admin);
        CsrfSession session = SignedIn.as(mockMvc, admin);

        try (AuditCapture audit = AuditCapture.start()) {
            tripTier2(admin, secret, session);
            assertThat(audit.withMessage(DISABLED_ROW)).singleElement().satisfies(row -> assertThat(row)
                    .containsEntry("user.id", admin.id().toString())
                    .containsEntry("log.level", "ERROR")
                    .containsEntry("event.severity", "critical"));
            assertThat(audit.withMessage(LOCKED_ROW)).isEmpty();
            assertThat(audit.withMessage(FAILED_ROW)).as("the disabling failure is still a failure").hasSize(1);
        }

        assertThat(accountRow(admin)).containsEntry("force_password_change", true)
                .containsEntry("credential_issued_at", null);
        mockMvc.perform(get("/api/profile").cookie(session.cookie()))
                .andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
        SignedIn.login(mockMvc, CsrfSession.bootstrap(mockMvc), admin.username(), admin.password())
                .andExpect(status().isOk()).andExpect(jsonPath("$.passwordChangeRequired").value(true));
    }

    @Test
    void aDisabledFactorIs423AtTheAdminEntryPointAndRebindRequiredOnTheSelfRead() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        byte[] secret = factors.enrol(admin);
        jdbc.update("UPDATE totp_user_details SET factor_disabled_at = ? WHERE user_id = ?",
                Timestamp.from(clock.instant()), admin.id());
        CsrfSession session = SignedIn.as(mockMvc, admin);

        mockMvc.perform(get("/api/admin/users").cookie(session.cookie()))
                .andExpect(problem(ErrorCode.FACTOR_DISABLED));
        mockMvc.perform(get("/api/profile").cookie(session.cookie())).andExpect(status().isOk())
                .andExpect(jsonPath("$.factors.enrolled").value(true))
                .andExpect(jsonPath("$.factors.rebindRequired").value(true));
        TotpFactors.verify(mockMvc, session, factors.code(secret)).andExpect(problem(ErrorCode.FACTOR_DISABLED));
    }

    @Test
    @Proves("T-AUD-021")
    void noSecretOrCiphertextReachesAnyAppenderOnTheVerifyFailLockAndDisableRows() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        byte[] secret = factors.enrol(admin);
        LogOutputGuard.register(new Base32().encodeAsString(secret));
        LogOutputGuard.register(Base64.getEncoder().encodeToString(jdbc.queryForObject(
                "SELECT totp_key FROM totp_user_details WHERE user_id = ?", byte[].class, admin.id())));
        CsrfSession session = SignedIn.as(mockMvc, admin);

        try (AuditCapture audit = AuditCapture.start()) {
            session = factors.verified(mockMvc, session, secret);
            failBelowTheLock(session, secret, TotpUserDetails.LOCK_THRESHOLD - 1);
            TotpFactors.verify(mockMvc, session, factors.wrongCode(secret))
                    .andExpect(problem(ErrorCode.TOO_MANY_REQUESTS));
            clock.advance(TotpUserDetails.LOCK);
            tripTier2(admin, secret, SignedIn.as(mockMvc, admin));

            assertThat(audit.rows()).extracting(row -> row.get("message")).contains("TOTP verification succeeded.",
                    "TOTP verification failed.", LOCKED_ROW, DISABLED_ROW);
        }
        assertThat(LogOutputGuard.scanNow(false)).isEmpty();
    }

    /** How the account's forced-change credential is replaced by a user-chosen password. */
    enum Completion {
        FORCED_CHANGE, TOKEN_REDEMPTION
    }

    @ParameterizedTest
    @EnumSource(Completion.class)
    @Proves("T-ADM-031")
    void aUserChosenPasswordClearsTheIssueTimeAndALaterTierTwoFlagNeverExpires(Completion completion)
            throws Exception {
        Account issued = accounts.withRole("ADMIN");
        byte[] secret = factors.enrol(issued);
        jdbc.update("UPDATE users SET force_password_change = TRUE, credential_issued_at = ? WHERE id = ?",
                Timestamp.from(clock.instant()), issued.id());
        switch (completion) {
            case FORCED_CHANGE -> mockMvc.perform(patch("/api/profile/password")
                            .with(SignedIn.as(mockMvc, issued).inHeader()).contentType(MediaType.APPLICATION_JSON)
                            .content(JSON.writeValueAsString(Map.of("currentPassword", issued.password(),
                                    "newPassword", NEW_PASSWORD))))
                    .andExpect(status().isNoContent());
            case TOKEN_REDEMPTION -> new PasswordResets(mockMvc)
                    .confirm(tokens.mint(issued.id(), CredentialTokenType.PASSWORD_RESET), NEW_PASSWORD)
                    .andExpect(status().isNoContent());
        }
        assertThat(accountRow(issued)).containsEntry("force_password_change", false)
                .containsEntry("credential_issued_at", null);
        Account admin = new Account(issued.id(), issued.username(), NEW_PASSWORD);

        tripTier2(admin, secret, SignedIn.as(mockMvc, admin));

        assertThat(accountRow(admin)).containsEntry("force_password_change", true)
                .containsEntry("credential_issued_at", null);
        clock.advance(UserAccount.FORCED_CHANGE_GRACE.plusDays(1));
        SignedIn.login(mockMvc, CsrfSession.bootstrap(mockMvc), admin.username(), admin.password())
                .andExpect(status().isOk()).andExpect(jsonPath("$.passwordChangeRequired").value(true));
    }
}
