package sg.securedhello.mfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.Map;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.web.servlet.MvcResult;

import sg.securedhello.error.ErrorCode;
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
 * Factor verification over the full context (ADR-021; R-MFA-017): a correct code grants {@code FACTOR_TOTP} and
 * rotates the session, a replay is refused under the row lock, and the account is always the session's.
 */
class TotpVerificationTest extends CtxDefaultTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TotpSecretCipher cipher;

    @Autowired
    private SessionRepository<? extends Session> sessions;

    private Accounts accounts;
    private TotpFactors factors;

    @BeforeEach
    void setUp() {
        accounts = new Accounts(jdbc, passwordEncoder);
        factors = new TotpFactors(jdbc, cipher, clock);
    }

    private SecurityContext storedContext(Cookie cookie) {
        Session session = sessions.findById(SessionRows.idOf(cookie.getValue()));
        assertThat(session).as("the stored session").isNotNull();
        return session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
    }

    private Long lastUsedCounter(Account account) {
        return jdbc.queryForObject("SELECT last_used_counter FROM totp_user_details WHERE user_id = ?", Long.class,
                account.id());
    }

    private static String wrongCode(String right) {
        return right.equals("000000") ? "000001" : "000000";
    }

    @Test
    @Proves("T-MFA-001")
    void aPasswordSignInHoldsTheRoleAndThePasswordFactorIssuedOnTheSharedClockAndNothingElse() throws Exception {
        clock.advance(Duration.ofMinutes(2));
        Instant signedInAt = clock.instant();
        CsrfSession session = SignedIn.as(mockMvc, accounts.withRole("ADMIN"));

        List<? extends GrantedAuthority> authorities = List.copyOf(
                storedContext(session.cookie()).getAuthentication().getAuthorities());
        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ADMIN", FactorGrantedAuthority.PASSWORD_AUTHORITY);
        assertThat(authorities).filteredOn(authority -> authority instanceof FactorGrantedAuthority).singleElement()
                .isInstanceOfSatisfying(FactorGrantedAuthority.class,
                        factor -> assertThat(factor.getIssuedAt()).isEqualTo(signedInAt));
    }

    @Test
    @Proves("T-MFA-005")
    void aCorrectCodeGrantsAFactorThatSurvivesTheJdbcSessionAndRotatesTheSessionId() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        byte[] secret = factors.enrol(admin);
        CsrfSession session = SignedIn.as(mockMvc, admin);
        clock.advance(Duration.ofSeconds(47));
        Instant grantedAt = clock.instant();

        MvcResult result = TotpFactors.verify(mockMvc, session, factors.code(secret))
                .andExpect(status().isNoContent()).andReturn();

        Cookie rotated = result.getResponse().getCookie("SESSION");
        assertThat(rotated).as("a new session cookie").isNotNull();
        assertThat(rotated.getValue()).isNotEqualTo(session.cookie().getValue());
        assertThat(new SessionRows(jdbc).exists(SessionRows.idOf(session.cookie().getValue()))).isFalse();
        assertThat(lastUsedCounter(admin)).isEqualTo(TotpWindow.counter(grantedAt));
        // Read back from SPRING_SESSION_ATTRIBUTES: the deserialised type and its issue time are intact.
        assertThat(storedContext(rotated).getAuthentication().getAuthorities())
                .filteredOn(authority -> TotpFactorGrant.AUTHORITY.equals(authority.getAuthority()))
                .singleElement().isInstanceOfSatisfying(FactorGrantedAuthority.class,
                        factor -> assertThat(factor.getIssuedAt()).isEqualTo(grantedAt));
        mockMvc.perform(get("/api/admin/users").cookie(rotated)).andExpect(status().isOk());
    }

    @Test
    @Proves("T-MFA-005")
    void aPlainAuthorityCarryingTheFactorsNameIsDeniedOnTheReadRule() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        byte[] secret = factors.enrol(admin);
        CsrfSession verified = factors.verified(mockMvc, SignedIn.as(mockMvc, admin), secret);
        TotpFactors.degrade(sessions, SessionRows.idOf(verified.cookie().getValue()));

        mockMvc.perform(get("/api/admin/users").cookie(verified.cookie()))
                .andExpect(problem(ErrorCode.MISSING_FACTOR))
                .andExpect(jsonPath("$.factor").value("TOTP"))
                .andExpect(jsonPath("$.reason").value("EXPIRED"));
    }

    @Test
    @Proves("T-MFA-014")
    void afterVerifyingAtCounterCTheCodesForCMinusOneAndCAreReplaysAndCPlusOneIsAccepted() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        byte[] secret = factors.enrol(admin);
        long c = TotpWindow.counter(clock.instant());
        CsrfSession session = factors.verified(mockMvc, SignedIn.as(mockMvc, admin), secret);

        for (long replayed : new long[] {c - 1, c}) {
            MvcResult refused = TotpFactors.verify(mockMvc, session, TotpFactors.code(secret, replayed))
                    .andExpect(problem(ErrorCode.INVALID_FACTOR)).andReturn();
            assertThat(refused.getResponse().getCookie("SESSION")).as("no rotation on a replay").isNull();
        }
        assertThat(lastUsedCounter(admin)).isEqualTo(c);

        TotpFactors.verify(mockMvc, session, TotpFactors.code(secret, c + 1)).andExpect(status().isNoContent());
        assertThat(lastUsedCounter(admin)).isEqualTo(c + 1);
    }

    @Test
    void aWrongCodeIsAnInvalidFactorWithNoGrantNoRotationAndAFailureRow() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        byte[] secret = factors.enrol(admin);
        CsrfSession session = SignedIn.as(mockMvc, admin);

        try (AuditCapture audit = AuditCapture.start()) {
            MvcResult refused = TotpFactors.verify(mockMvc, session, wrongCode(factors.code(secret)))
                    .andExpect(problem(ErrorCode.INVALID_FACTOR)).andReturn();
            TotpFactors.verify(mockMvc, session, "").andExpect(problem(ErrorCode.INVALID_FACTOR));

            assertThat(refused.getResponse().getCookie("SESSION")).isNull();
            assertThat(audit.withMessage("TOTP verification failed.")).hasSize(2)
                    .allSatisfy(row -> assertThat(row).containsEntry("user.id", admin.id().toString())
                            .containsEntry("event.outcome", "failure"));
            assertThat(audit.withMessage("TOTP verification succeeded.")).isEmpty();
        }
        assertThat(lastUsedCounter(admin)).isNull();
        assertThat(storedContext(session.cookie()).getAuthentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority).doesNotContain(TotpFactorGrant.AUTHORITY);
        mockMvc.perform(get("/api/admin/users").cookie(session.cookie()))
                .andExpect(problem(ErrorCode.MISSING_FACTOR));
    }

    @Test
    void aSuccessWritesTheVerifiedRow() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        byte[] secret = factors.enrol(admin);
        CsrfSession session = SignedIn.as(mockMvc, admin);

        try (AuditCapture audit = AuditCapture.start()) {
            factors.verified(mockMvc, session, secret);

            assertThat(audit.withMessage("TOTP verification succeeded.")).singleElement()
                    .satisfies(row -> assertThat(row).containsEntry("user.id", admin.id().toString())
                            .containsEntry("event.action", "totp-verify"));
        }
    }

    @Test
    void theAccountIsTheSessionsNeverOneNamedInTheBody() throws Exception {
        Account caller = accounts.withRole("ADMIN");
        Account other = accounts.withRole("ADMIN");
        factors.enrol(caller);
        byte[] othersSecret = factors.enrol(other);
        CsrfSession session = SignedIn.as(mockMvc, caller);

        mockMvc.perform(post(TotpFactors.VERIFICATION).with(session.inHeader())
                        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(Map.of(
                                "code", factors.code(othersSecret), "username", other.username(),
                                "userId", other.id().toString()))))
                .andExpect(problem(ErrorCode.INVALID_FACTOR));

        assertThat(lastUsedCounter(other)).isNull();
    }

    @Test
    void anUnenrolledAdminIsToldToEnrolAndAUserIsRefused() throws Exception {
        TotpFactors.verify(mockMvc, SignedIn.as(mockMvc, accounts.withRole("ADMIN")), "123456")
                .andExpect(problem(ErrorCode.FACTOR_ENROLMENT_REQUIRED));
        TotpFactors.verify(mockMvc, SignedIn.as(mockMvc, accounts.user()), "123456")
                .andExpect(problem(ErrorCode.ACCESS_DENIED));
        TotpFactors.verify(mockMvc, CsrfSession.bootstrap(mockMvc), "123456")
                .andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
    }

    @Test
    void concurrentRequestsCarryingOneCodeGrantItOnceUnderTheRowLock() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        byte[] secret = factors.enrol(admin);
        CsrfSession session = SignedIn.as(mockMvc, admin);
        long c = TotpWindow.counter(clock.instant());
        String code = factors.code(secret);
        int racers = 4;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        List<Future<Integer>> statuses = new ArrayList<>();
        try {
            for (int i = 0; i < racers; i++) {
                statuses.add(pool.submit(() -> {
                    start.await();
                    return TotpFactors.verify(mockMvc, session, code).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> done = new ArrayList<>();
            for (Future<Integer> status : statuses) {
                done.add(status.get());
            }
            // One grant; every other racer is a replay (412) or, once the winner rotated the id, a dead session (401).
            assertThat(done).filteredOn(status -> status == 204).hasSize(1);
            assertThat(done).filteredOn(status -> status != 204).isSubsetOf(ErrorCode.INVALID_FACTOR.status(),
                    ErrorCode.AUTHENTICATION_FAILED.status());
        } finally {
            pool.shutdownNow();
        }
        assertThat(lastUsedCounter(admin)).isEqualTo(c);
    }

    @Test
    void aDisabledFactorIsRefusedBeforeAnyCodeIsChecked() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        byte[] secret = factors.enrol(admin);
        jdbc.update("UPDATE totp_user_details SET factor_disabled_at = ? WHERE user_id = ?",
                Timestamp.from(clock.instant()), admin.id());

        TotpFactors.verify(mockMvc, SignedIn.as(mockMvc, admin), factors.code(secret))
                .andExpect(problem(ErrorCode.FACTOR_DISABLED));
        assertThat(lastUsedCounter(admin)).isNull();
    }

    @Test
    void aCodeLongerThanSixCharactersIsAValidationFailure() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        factors.enrol(admin);

        TotpFactors.verify(mockMvc, SignedIn.as(mockMvc, admin), "1234567")
                .andExpect(problem(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void aMissingCodeIsAValidationFailure() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        factors.enrol(admin);

        mockMvc.perform(post(TotpFactors.VERIFICATION).with(SignedIn.as(mockMvc, admin).inHeader())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(problem(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void aFailedEnrolmentConfirmationIsAudited() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        CsrfSession session = SignedIn.as(mockMvc, admin);

        try (AuditCapture audit = AuditCapture.start()) {
            mockMvc.perform(post("/api/mfa/totp/enrolment/confirmation").with(session.inHeader())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                    .andExpect(problem(ErrorCode.INVALID_FACTOR));

            assertThat(audit.withMessage("TOTP enrolment confirmation failed.")).singleElement()
                    .satisfies(row -> assertThat(row).containsEntry("user.id", admin.id().toString()));
        }
    }
}
