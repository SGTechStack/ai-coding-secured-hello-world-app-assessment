package sg.securedhello.security.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.EcsJson;
import sg.securedhello.testsupport.LogOutputGuard;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SessionRows;
import sg.securedhello.testsupport.SignedIn;

/** The audit rows of sign-in and sign-out: rows 1, 2, 7 and 8 of the catalogue (ADR-055). */
class SignInAuditTest extends CtxDefaultTest {

    private static final String LOGIN_SUCCEEDED = "Login succeeded.";
    private static final String LOGIN_FAILED = "Login failed.";
    private static final String SESSION_STARTED = "Session started.";
    private static final String LOGOUT_SUCCEEDED = "Logout succeeded.";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuditEmitter auditEmitter;

    private Accounts accounts;

    @BeforeEach
    void setUp() {
        accounts = new Accounts(jdbc, passwordEncoder);
    }

    private static Map<String, Object> only(List<Map<String, Object>> rows) {
        assertThat(rows).hasSize(1);
        return rows.getFirst();
    }

    @Test
    void aSignInWritesSessionStartBeforeTheRotationAndLoginSuccessAfterIt() throws Exception {
        Account alice = accounts.user();
        try (AuditCapture audit = AuditCapture.start()) {
            SignedIn.as(mockMvc, alice);

            Map<String, Object> start = only(audit.withMessage(SESSION_STARTED));
            Map<String, Object> success = only(audit.withMessage(LOGIN_SUCCEEDED));
            assertThat(start).containsEntry("event.action", "session-start").containsEntry("event.reason", "LOGIN")
                    .containsEntry("user.id", alice.id().toString()).containsKey("session.hash");
            assertThat(success).containsEntry("event.action", "user-authentication")
                    .containsEntry("event.outcome", "success").containsEntry("user.id", alice.id().toString())
                    .containsEntry("url.path", "/api/login").containsKey("session.hash");
            assertThat(success.get("session.hash")).as("pre- and post-rotation hashes differ")
                    .isNotEqualTo(start.get("session.hash"));
        }
    }

    @Test
    @Proves("T-AUD-009")
    void aFailedLoginForAnUnknownUsernameCarriesNoUserId() throws Exception {
        try (AuditCapture audit = AuditCapture.start()) {
            SignedIn.login(mockMvc, CsrfSession.bootstrap(mockMvc), Accounts.unknownUsername(), Accounts.PASSWORD);

            assertThat(only(audit.withMessage(LOGIN_FAILED))).containsEntry("event.outcome", "failure")
                    .containsEntry("log.level", "WARN").containsEntry("event.reason", "UNKNOWN_USER")
                    .doesNotContainKeys("user.id", "user.hash", "user.name");
        }
    }

    @Test
    @Proves("T-AUD-025")
    void aFailedLoginForAnExistingAccountCarriesItsUserIdAndTheInternalReason() throws Exception {
        Account known = accounts.user();
        Account disabled = accounts.disabled();
        Account pending = accounts.notActivated();
        try (AuditCapture audit = AuditCapture.start()) {
            SignedIn.login(mockMvc, CsrfSession.bootstrap(mockMvc), known.username(), Accounts.WRONG_PASSWORD);
            SignedIn.login(mockMvc, CsrfSession.bootstrap(mockMvc), disabled.username(), Accounts.PASSWORD);
            SignedIn.login(mockMvc, CsrfSession.bootstrap(mockMvc), pending.username(), Accounts.PASSWORD);

            assertThat(audit.withMessage(LOGIN_FAILED))
                    .extracting(row -> row.get("user.id"), row -> row.get("event.reason"))
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(known.id().toString(), "BAD_CREDENTIALS"),
                            org.assertj.core.groups.Tuple.tuple(disabled.id().toString(), "ACCOUNT_DISABLED"),
                            org.assertj.core.groups.Tuple.tuple(pending.id().toString(), "ACCOUNT_DISABLED"));
        }
    }

    @Test
    void logoutWritesItsRowWithTheUserId() throws Exception {
        Account alice = accounts.user();
        CsrfSession session = SignedIn.as(mockMvc, alice);
        try (AuditCapture audit = AuditCapture.start()) {
            mockMvc.perform(post("/api/logout").with(session.inHeader())).andExpect(status().isNoContent());

            assertThat(only(audit.withMessage(LOGOUT_SUCCEEDED))).containsEntry("event.action", "user-logout")
                    .containsEntry("user.id", alice.id().toString()).containsKey("session.hash");
        }
    }

    @Test
    @Proves("T-AUD-008")
    void noAuditRowOfTheFlowCarriesAUsernameOrEmail() throws Exception {
        Account alice = accounts.user();
        try (AuditCapture audit = AuditCapture.start()) {
            SignedIn.login(mockMvc, CsrfSession.bootstrap(mockMvc), alice.username(), Accounts.WRONG_PASSWORD);
            CsrfSession session = SignedIn.as(mockMvc, alice);
            mockMvc.perform(post("/api/logout").cookie(session.cookie())); // a CSRF refusal, row 13
            mockMvc.perform(post("/api/logout").with(session.inHeader())).andExpect(status().isNoContent());
            auditEmitter.closeKeyingWindow(); // row 13 is keyed, written as its window closes (ADR-019)

            assertThat(audit.rows()).hasSizeGreaterThanOrEqualTo(5).allSatisfy(row -> {
                assertThat(row).doesNotContainKeys("user.name", "user.hash", "user.email");
                assertThat(row.values().toString()).doesNotContain(alice.username());
            });
        }
    }

    @Test
    @Proves("T-AUD-005")
    void theLoginSuccessRowReachesTheDedicatedAuditFile() throws Exception {
        Account alice = accounts.user();

        SignedIn.as(mockMvc, alice);

        Path file = Path.of(System.getProperty("app.audit.directory"), "audit.ndjson");
        assertThat(EcsJson.rows(Files.readString(file, StandardCharsets.UTF_8)))
                .filteredOn(row -> LOGIN_SUCCEEDED.equals(row.get("message"))
                        && alice.id().toString().equals(row.get("user.id")))
                .hasSize(1);
    }

    /** {@link LogOutputGuard} fails this test if the session id or a CSRF token reaches any output. */
    @Test
    @Proves("T-AUD-020")
    void neitherTheCsrfTokenNorTheRawSessionIdIsLoggedAcrossASignedInFlow() throws Exception {
        Account alice = accounts.user();
        try (AuditCapture audit = AuditCapture.start()) {
            CsrfSession anonymous = CsrfSession.bootstrap(mockMvc);
            register(anonymous);
            Cookie signedIn = SignedIn.login(mockMvc, anonymous, alice.username(), alice.password()).andReturn()
                    .getResponse().getCookie("SESSION");
            CsrfSession session = SignedIn.refreshed(mockMvc, signedIn);
            register(session);
            mockMvc.perform(post("/api/logout").with(session.inHeader())).andExpect(status().isNoContent());

            assertThat(audit.rows()).isNotEmpty().allSatisfy(row -> assertThat(row).containsKey("session.hash"));
        }
    }

    private static void register(CsrfSession session) {
        LogOutputGuard.register(session.token());
        LogOutputGuard.register(session.cookie().getValue());
        LogOutputGuard.register(SessionRows.idOf(session.cookie().getValue()));
    }
}
