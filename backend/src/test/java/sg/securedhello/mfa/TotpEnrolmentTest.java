package sg.securedhello.mfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;

import jakarta.servlet.http.Cookie;

import org.apache.commons.codec.binary.Base32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.encrypt.AesGcmBytesEncryptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import sg.securedhello.audit.AuditEvent;
import sg.securedhello.error.ErrorCode;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.LogOutputGuard;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SessionRows;
import sg.securedhello.testsupport.SignedIn;
import sg.securedhello.testsupport.TestSecrets;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * TOTP enrolment over the full context (ADR-025; ADR-028; REJ-071): provisioning writes a pending enrolment sealed with
 * its context prefix, and a correct code binds it, grants the factor and rotates the session.
 */
class TotpEnrolmentTest extends CtxDefaultTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ENROLMENT = "/api/mfa/totp/enrolment";
    private static final String CONFIRMATION = "/api/mfa/totp/enrolment/confirmation";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SessionRepository<? extends Session> sessions;

    private Accounts accounts;

    @BeforeEach
    void setUp() {
        accounts = new Accounts(jdbc, passwordEncoder);
    }

    /** A provisioning response, parsed. */
    private record Provisioned(String otpauthUri, String secretBase32, byte[] qrPng) {

        byte[] secret() {
            return new Base32().decode(secretBase32);
        }
    }

    private ResultActions provisionRequest(CsrfSession session) throws Exception {
        return mockMvc.perform(post(ENROLMENT).with(session.inHeader()));
    }

    private Provisioned provision(CsrfSession session) throws Exception {
        MvcResult result = provisionRequest(session).andExpect(status().isOk()).andReturn();
        JsonNode body = JSON.readTree(result.getResponse().getContentAsString());
        Provisioned provisioned = new Provisioned(body.get("otpauthUri").asString(),
                body.get("secretBase32").asString(), Base64.getDecoder().decode(body.get("qrPng").asString()));
        LogOutputGuard.register(provisioned.secretBase32());
        LogOutputGuard.register(provisioned.otpauthUri());
        return provisioned;
    }

    private ResultActions confirm(CsrfSession session, String code) throws Exception {
        return mockMvc.perform(post(CONFIRMATION).with(session.inHeader()).contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("code", code))));
    }

    private String codeFor(byte[] secret) {
        return TotpWindow.generate(secret, TotpWindow.counter(clock.instant()), TotpWindow.DIGITS);
    }

    private byte[] pendingKey(UUID userId) {
        return jdbc.queryForObject("SELECT totp_key FROM pending_totp WHERE user_id = ?", byte[].class, userId);
    }

    private int rows(String table, UUID userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE user_id = ?", Integer.class, userId);
    }

    private SecurityContext storedContext(Cookie cookie) {
        Session session = sessions.findById(SessionRows.idOf(cookie.getValue()));
        assertThat(session).as("the stored session").isNotNull();
        return session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
    }

    @Test
    @Proves("T-MFA-017")
    void provisioningReturnsAPngAndSealsTheShownSecretUnderTheConfiguredKey() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        Provisioned provisioned = provision(SignedIn.as(mockMvc, admin));

        assertThat(ImageIO.read(new ByteArrayInputStream(provisioned.qrPng()))).as("a decodable PNG").isNotNull();
        assertThat(provisioned.qrPng()).startsWith(0x89, 'P', 'N', 'G');
        assertThat(provisioned.secret()).hasSize(20);
        assertThat(provisioned.otpauthUri()).isEqualTo("otpauth://totp/Secured%20Hello%20World:" + admin.username()
                + "?secret=" + provisioned.secretBase32()
                + "&issuer=Secured%20Hello%20World&algorithm=SHA1&digits=6&period=30");

        byte[] envelope = pendingKey(admin.id());
        assertThat(envelope).hasSize(69);
        assertThat(jdbc.queryForObject("SELECT key_version FROM pending_totp WHERE user_id = ?", Integer.class,
                admin.id())).isEqualTo(1);
        byte[] key = Base64.getDecoder().decode(TestSecrets.TOTP_KEY);
        ByteBuffer plaintext = ByteBuffer.wrap(AesGcmBytesEncryptor
                .withSecretKey(new SecretKeySpec(key, "AES")).build().decrypt(envelope));
        assertThat(plaintext.remaining()).isEqualTo(37);
        assertThat(new UUID(plaintext.getLong(), plaintext.getLong())).isEqualTo(admin.id());
        assertThat(plaintext.get()).isEqualTo((byte) 1);
        byte[] secret = new byte[20];
        plaintext.get(secret);
        assertThat(secret).isEqualTo(provisioned.secret());
    }

    @Test
    @Proves("T-AUD-021")
    void theSecretIsSentOnceWithNoStoreAndNeverReachesTheLogsOrTheAuditRows() throws Exception {
        CsrfSession session = SignedIn.as(mockMvc, accounts.withRole("ADMIN"));
        try (AuditCapture audit = AuditCapture.start()) {
            MvcResult result = provisionRequest(session)
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString(
                            "no-store")))
                    .andReturn();
            JsonNode body = JSON.readTree(result.getResponse().getContentAsString());
            String secret = body.get("secretBase32").asString();
            String uri = body.get("otpauthUri").asString();
            LogOutputGuard.register(secret);
            LogOutputGuard.register(uri);
            confirm(session, codeFor(new Base32().decode(secret))).andExpect(status().isNoContent());

            assertThat(audit.withMessage("TOTP enrolment provisioned.")).hasSize(1);
            assertThat(audit.withMessage("TOTP enrolment confirmed.")).hasSize(1);
            assertThat(audit.rows().toString()).doesNotContain(secret);
        }
        assertThat(LogOutputGuard.scanNow(false)).isEmpty();
    }

    @Test
    void provisioningTwiceReplacesThePendingRowAndResetsNoCounter() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        CsrfSession session = SignedIn.as(mockMvc, admin);
        jdbc.update("UPDATE users SET failed_login_attempts = 2, consecutive_failures_since_success = 3"
                + " WHERE id = ?", admin.id());
        Map<String, Object> countersBefore = counters(admin);

        Provisioned first = provision(session);
        byte[] firstKey = pendingKey(admin.id());
        Provisioned second = provision(session);

        assertThat(rows("pending_totp", admin.id())).isEqualTo(1);
        assertThat(second.secretBase32()).isNotEqualTo(first.secretBase32());
        assertThat(pendingKey(admin.id())).hasSize(69).isNotEqualTo(firstKey);
        assertThat(rows("totp_user_details", admin.id())).isZero();
        assertThat(counters(admin)).isEqualTo(countersBefore);
        // Only the newest secret confirms.
        confirm(session, codeFor(first.secret())).andExpect(problem(ErrorCode.INVALID_FACTOR));
        assertThat(counters(admin)).isEqualTo(countersBefore);
    }

    private Map<String, Object> counters(Account account) {
        return jdbc.queryForMap("SELECT failed_login_attempts, last_failed_at, locked_until,"
                + " consecutive_failures_since_success, password_disabled_at FROM users WHERE id = ?", account.id());
    }

    @Test
    @Proves("T-MFA-008")
    void aCorrectCodeBindsThePendingEnvelopeGrantsTheFactorAtTheClocksInstantAndRotatesTheSession()
            throws Exception {
        Account admin = accounts.withRole("ADMIN");
        CsrfSession session = SignedIn.as(mockMvc, admin);
        Provisioned provisioned = provision(session);
        byte[] pending = pendingKey(admin.id());
        clock.advance(Duration.ofMinutes(3));
        Instant grantedAt = clock.instant();

        MvcResult result = confirm(session, codeFor(provisioned.secret())).andExpect(status().isNoContent())
                .andReturn();

        Cookie rotated = result.getResponse().getCookie("SESSION");
        assertThat(rotated).as("a new session cookie").isNotNull();
        assertThat(rotated.getValue()).isNotEqualTo(session.cookie().getValue());
        assertThat(new SessionRows(jdbc).exists(SessionRows.idOf(session.cookie().getValue()))).isFalse();
        // Enrolment binding: the pending envelope, copied verbatim; the pending row is gone.
        assertThat(jdbc.queryForObject("SELECT totp_key FROM totp_user_details WHERE user_id = ?", byte[].class,
                admin.id())).isEqualTo(pending);
        assertThat(rows("pending_totp", admin.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT last_used_counter FROM totp_user_details WHERE user_id = ?",
                Long.class, admin.id())).isEqualTo(TotpWindow.counter(grantedAt));
        // The factor, as the JDBC session stores it: a FactorGrantedAuthority issued at the shared clock's instant.
        List<? extends GrantedAuthority> factors = storedContext(rotated).getAuthentication().getAuthorities()
                .stream().filter(authority -> TotpFactorGrant.AUTHORITY.equals(authority.getAuthority())).toList();
        assertThat(factors).singleElement().isInstanceOfSatisfying(FactorGrantedAuthority.class,
                factor -> assertThat(factor.getIssuedAt()).isEqualTo(grantedAt));
        assertThat(storedContext(rotated).getAuthentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority).contains("ROLE_ADMIN", "FACTOR_PASSWORD");
    }

    @Test
    @Proves("T-MFA-016")
    void aWrongCodeIsRefusedAndKeepsThePendingRowAndTheSessionSoARetrySucceeds() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        CsrfSession session = SignedIn.as(mockMvc, admin);
        Provisioned provisioned = provision(session);
        byte[] pending = pendingKey(admin.id());
        String right = codeFor(provisioned.secret());
        String wrong = right.equals("000000") ? "000001" : "000000";

        MvcResult refused = confirm(session, wrong).andExpect(problem(ErrorCode.INVALID_FACTOR)).andReturn();

        assertThat(refused.getResponse().getCookie("SESSION")).as("no rotation").isNull();
        assertThat(pendingKey(admin.id())).isEqualTo(pending);
        assertThat(rows("totp_user_details", admin.id())).isZero();
        assertThat(storedContext(session.cookie()).getAuthentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority).doesNotContain(TotpFactorGrant.AUTHORITY);

        confirm(session, right).andExpect(status().isNoContent());
        assertThat(rows("totp_user_details", admin.id())).isEqualTo(1);
    }

    @Test
    void aConfirmationWithNoPendingEnrolmentIsAnInvalidFactor() throws Exception {
        CsrfSession session = SignedIn.as(mockMvc, accounts.withRole("ADMIN"));

        confirm(session, "123456").andExpect(problem(ErrorCode.INVALID_FACTOR));
    }

    @Test
    void anEnrolledAdminCannotProvisionOrConfirmAgain() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        CsrfSession session = SignedIn.as(mockMvc, admin);
        Provisioned provisioned = provision(session);
        MvcResult result = confirm(session, codeFor(provisioned.secret())).andExpect(status().isNoContent())
                .andReturn();
        CsrfSession enrolled = SignedIn.refreshed(mockMvc, result.getResponse().getCookie("SESSION"));
        byte[] factorKey = jdbc.queryForObject("SELECT totp_key FROM totp_user_details WHERE user_id = ?",
                byte[].class, admin.id());

        provisionRequest(enrolled).andExpect(problem(ErrorCode.FACTOR_ALREADY_ENROLLED));
        clock.advance(Duration.ofSeconds(TotpWindow.STEP_SECONDS));
        confirm(enrolled, codeFor(provisioned.secret())).andExpect(problem(ErrorCode.FACTOR_ALREADY_ENROLLED));

        assertThat(rows("pending_totp", admin.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT totp_key FROM totp_user_details WHERE user_id = ?", byte[].class,
                admin.id())).isEqualTo(factorKey);
    }

    @Test
    @Proves("T-AUD-021")
    void anEnvelopeCopiedUnderAnotherUsersRowFailsToOpenAndEmitsTheMismatchRow() throws Exception {
        Account owner = accounts.withRole("ADMIN");
        Account other = accounts.withRole("ADMIN");
        Provisioned owners = provision(SignedIn.as(mockMvc, owner));
        CsrfSession otherSession = SignedIn.as(mockMvc, other);
        provision(otherSession);
        jdbc.update("UPDATE pending_totp SET totp_key = ? WHERE user_id = ?", pendingKey(owner.id()), other.id());

        try (AuditCapture audit = AuditCapture.start()) {
            confirm(otherSession, codeFor(owners.secret())).andExpect(problem(ErrorCode.INTERNAL_ERROR));

            List<Map<String, Object>> mismatches = audit.withMessage("TOTP secret context mismatch.");
            assertThat(mismatches).singleElement().satisfies(row -> {
                assertThat(row).containsEntry("user.id", other.id().toString())
                        .containsEntry("log.level", "ERROR")
                        .containsEntry("event.action", AuditEvent.TOTP_CONTEXT_MISMATCH.definition().action());
            });
        }
        assertThat(rows("totp_user_details", other.id())).isZero();
    }

    @Test
    void aUserIsRefusedBothRoutes() throws Exception {
        CsrfSession session = SignedIn.as(mockMvc, accounts.user());

        provisionRequest(session).andExpect(problem(ErrorCode.ACCESS_DENIED));
        confirm(session, "123456").andExpect(problem(ErrorCode.ACCESS_DENIED));
    }

    @Test
    void aForcedChangeAdminMustChangeThePasswordBeforeEnrolling() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        jdbc.update("UPDATE users SET force_password_change = TRUE, credential_issued_at = ? WHERE id = ?",
                java.sql.Timestamp.from(clock.instant()), admin.id());
        CsrfSession session = SignedIn.as(mockMvc, admin);

        provisionRequest(session).andExpect(problem(ErrorCode.PASSWORD_CHANGE_REQUIRED));
        confirm(session, "123456").andExpect(problem(ErrorCode.PASSWORD_CHANGE_REQUIRED));
        assertThat(rows("pending_totp", admin.id())).isZero();
    }
}
