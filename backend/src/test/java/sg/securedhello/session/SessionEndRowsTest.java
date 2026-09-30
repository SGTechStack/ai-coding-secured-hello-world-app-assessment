package sg.securedhello.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.error.ErrorCode;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SignedIn;

/**
 * The session-ending rows that name an account (R-AUD-003): row 10 when a newer sign-in displaces a session, written
 * at displacement inside the login composite, and row 9 when the absolute-lifetime filter ends a session.
 */
class SessionEndRowsTest extends CtxDefaultTest {

    private static final String EVICTED = "Session ended by a newer sign-in.";
    private static final String EXPIRED = "Session ended at its absolute lifetime.";
    private static final String SESSION_START = "Session started.";
    private static final String LOGIN_FAILED = "Login failed.";
    private static final String LOGIN_SUCCEEDED = "Login succeeded.";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SessionLifetimeProperties lifetime;

    @Autowired
    private AuditEmitter emitter;

    private Accounts accounts;

    @BeforeEach
    void setUp() {
        accounts = new Accounts(jdbc, passwordEncoder);
    }

    @Test
    @Proves("T-AUD-015")
    void aDisplacingSignInWritesRow10AtDisplacementAndTheStartRowJoinsThePreLoginAttempts() throws Exception {
        Account alice = accounts.user();
        CsrfSession first = SignedIn.as(mockMvc, alice);
        CsrfSession second = CsrfSession.bootstrap(mockMvc);

        // Keyed rows the rest of the context left open are written now, not in the middle of the capture.
        emitter.closeKeyingWindow();
        List<Map<String, Object>> rows;
        try (AuditCapture audit = AuditCapture.start()) {
            SignedIn.login(mockMvc, second, alice.username(), Accounts.WRONG_PASSWORD)
                    .andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
            SignedIn.login(mockMvc, second, alice.username(), alice.password()).andExpect(status().isOk());
            rows = audit.rows();
        }

        assertThat(rows).extracting(row -> row.get("message"))
                .containsExactly(LOGIN_FAILED, EVICTED, SESSION_START, LOGIN_SUCCEEDED);
        Object preLogin = rows.get(0).get("session.hash");
        assertThat(preLogin).as("the failed attempt carries the pre-login session's hash").isNotNull();
        assertThat(rows.get(1)).containsEntry("user.id", alice.id().toString())
                .containsEntry("event.action", "session-end")
                .containsEntry("event.reason", "CONCURRENT_EVICTION");
        assertThat(rows.get(2)).as("the session-start step runs before the id rotates")
                .containsEntry("session.hash", preLogin);
        assertThat(rows.get(3).get("session.hash")).as("the success row carries the rotated id's hash")
                .isNotNull().isNotEqualTo(preLogin);
        mockMvc.perform(get("/api/hello").cookie(first.cookie())).andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
    }

    @Test
    void aFirstSignInDisplacesNothingAndWritesNoRow10() throws Exception {
        Account bob = accounts.user();
        try (AuditCapture audit = AuditCapture.start()) {
            SignedIn.as(mockMvc, bob);

            assertThat(audit.withMessage(EVICTED)).isEmpty();
        }
    }

    @Test
    void theAbsoluteLifetimeFilterWritesRow9ForTheSessionItEnds() throws Exception {
        Account carol = accounts.user();
        Cookie signedIn = SignedIn.as(mockMvc, carol).cookie();

        clock.advance(lifetime.absolute());
        emitter.closeKeyingWindow();
        try (AuditCapture audit = AuditCapture.start()) {
            mockMvc.perform(get("/api/hello").cookie(signedIn)).andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));

            assertThat(audit.rows()).singleElement().satisfies(row -> assertThat(row)
                    .containsEntry("message", EXPIRED)
                    .containsEntry("event.action", "session-end")
                    .containsEntry("event.type", List.of("end"))
                    .containsEntry("event.reason", "ABSOLUTE_TIMEOUT")
                    .containsEntry("user.id", carol.id().toString())
                    .containsKey("session.hash"));
        }
    }
}
