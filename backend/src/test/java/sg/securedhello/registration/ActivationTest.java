package sg.securedhello.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import sg.securedhello.credential.CredentialTokenType;
import sg.securedhello.error.ErrorCode;
import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.Registrations;
import sg.securedhello.testsupport.SignedIn;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@code POST /api/register/activate} through the full context (ADR-007; ADR-032): the token is consumed first, then
 * the password is set through {@code PasswordService}, in one transaction; only then can the account sign in.
 */
class ActivationTest extends CtxDefaultTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String LOOPBACK = "127.0.0.1";

    @Autowired
    private JdbcTemplate jdbc;

    private Registrations registrations;

    @BeforeEach
    void setUp() {
        registrations = new Registrations(mockMvc);
    }

    /** A pending registration: its username and the activation token its email received. */
    private record Pending(String username, String token) {
    }

    private Pending pending() throws Exception {
        String username = Registrations.freshUsername();
        String email = Registrations.emailFor(username);
        registrations.register(username, email).andExpect(status().isAccepted());
        return new Pending(username, emails.latestToken(email, CredentialTokenType.ACTIVATION).orElseThrow());
    }

    private int login(String username, String password) throws Exception {
        return SignedIn.loginFrom(mockMvc, LOOPBACK, username, password).andReturn().getResponse().getStatus();
    }

    private Object activatedAt(String username) {
        return jdbc.queryForObject("SELECT activated_at FROM users WHERE username = ?", Object.class, username);
    }

    @Test
    void redeemingTheTokenSetsThePasswordActivatesTheAccountAndLetsItSignIn() throws Exception {
        Pending pending = pending();

        registrations.activate(pending.token(), Registrations.PASSWORD).andExpect(status().isNoContent());

        assertThat(activatedAt(pending.username())).isNotNull();
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE username = ?", String.class,
                pending.username())).startsWith("{bcrypt}");
        assertThat(login(pending.username(), Registrations.PASSWORD)).isEqualTo(200);
    }

    @Test
    void theActivationTokenWorksOnce() throws Exception {
        Pending pending = pending();
        registrations.activate(pending.token(), Registrations.PASSWORD).andExpect(status().isNoContent());

        registrations.activate(pending.token(), "another harbour quietly hums along")
                .andExpect(problem(ErrorCode.RESET_TOKEN_INVALID));

        assertThat(login(pending.username(), Registrations.PASSWORD)).as("the first password stands").isEqualTo(200);
    }

    @Test
    void theActivationTokenExpiresTwentyFourHoursAfterIssueOnTheClock() throws Exception {
        Pending inTime = pending();
        Pending late = pending();

        clock.advance(Duration.ofHours(24).minusSeconds(1));
        registrations.activate(inTime.token(), Registrations.PASSWORD).andExpect(status().isNoContent());

        clock.advance(Duration.ofSeconds(1));
        registrations.activate(late.token(), Registrations.PASSWORD).andExpect(problem(ErrorCode.RESET_TOKEN_INVALID));
        assertThat(activatedAt(late.username())).isNull();
    }

    @Test
    void anUnknownOrMisshapenTokenIsInvalid() throws Exception {
        registrations.activate("A".repeat(43), Registrations.PASSWORD).andExpect(problem(ErrorCode.RESET_TOKEN_INVALID));
        registrations.activate("not a token", Registrations.PASSWORD).andExpect(problem(ErrorCode.RESET_TOKEN_INVALID));
        registrations.activate(pending().token() + "x", Registrations.PASSWORD)
                .andExpect(problem(ErrorCode.RESET_TOKEN_INVALID));
    }

    @Test
    void aTokenOfAnotherTypeDoesNotRedeemAsAnActivationToken() throws Exception {
        Pending pending = pending();
        // The same plaintext stored as a reset token: its hash differs, so it is simply not found.
        jdbc.update("UPDATE credential_tokens SET type = 'PASSWORD_RESET' WHERE user_id ="
                + " (SELECT id FROM users WHERE username = ?)", pending.username());

        registrations.activate(pending.token(), Registrations.PASSWORD).andExpect(problem(ErrorCode.RESET_TOKEN_INVALID));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM credential_tokens t JOIN users u ON u.id = t.user_id"
                + " WHERE u.username = ? AND t.used_at IS NULL", Integer.class, pending.username())).isOne();
    }

    @Test
    @Proves("T-CRED-015")
    void aRejectedPasswordDoesNotBurnTheToken() throws Exception {
        Pending pending = pending();

        registrations.activate(pending.token(), "too short").andExpect(problem(ErrorCode.PASSWORD_REJECTED))
                .andExpect(jsonPath("$.rule").value("MIN_LENGTH"));
        registrations.activate(pending.token(), pending.username() + " is my password")
                .andExpect(problem(ErrorCode.PASSWORD_REJECTED)).andExpect(jsonPath("$.rule").value("CONTEXT_TERM"));
        assertThat(activatedAt(pending.username())).isNull();

        registrations.activate(pending.token(), Registrations.PASSWORD).andExpect(status().isNoContent());
        assertThat(activatedAt(pending.username())).isNotNull();
    }

    @Test
    void theTokenIsCheckedBeforeThePasswordSoAStrengthErrorNeverConfirmsAToken() throws Exception {
        try (AuditCapture audit = AuditCapture.start()) {
            registrations.activate("B".repeat(43), "too short").andExpect(problem(ErrorCode.RESET_TOKEN_INVALID));

            assertThat(audit.rows()).as("no password-rejected row, nor any other, before a successful token check")
                    .isEmpty();
        }
    }

    @Test
    @Proves("T-CRED-007")
    void aPasswordSetAtActivationKeepsItsSpacesAndCase() throws Exception {
        Pending pending = pending();
        String password = "  Velvet Harbour Quietly Hums  ";

        registrations.activate(pending.token(), password).andExpect(status().isNoContent());

        assertThat(login(pending.username(), password)).isEqualTo(200);
        assertThat(login(pending.username(), password.strip())).as("trimmed").isEqualTo(401);
        assertThat(login(pending.username(), password.toLowerCase(java.util.Locale.ROOT))).as("lowercased")
                .isEqualTo(401);
    }

    @Test
    void aPendingAccountCannotSignInAndGetsTheUniform401() throws Exception {
        Pending pending = pending();

        MvcResult asPending = SignedIn.loginFrom(mockMvc, LOOPBACK, pending.username(), Registrations.PASSWORD)
                .andExpect(problem(ErrorCode.AUTHENTICATION_FAILED)).andReturn();
        MvcResult asNobody = SignedIn.loginFrom(mockMvc, LOOPBACK, "nobody-" + pending.username(),
                Registrations.PASSWORD).andExpect(problem(ErrorCode.AUTHENTICATION_FAILED)).andReturn();

        assertThat(withoutTraceId(asPending)).isEqualTo(withoutTraceId(asNobody));
    }

    @Test
    @Proves("T-CRED-014")
    void concurrentRedemptionsOfOneTokenLetExactlyOneSucceed() throws Exception {
        Pending pending = pending();
        int racers = 6;
        List<CsrfSession> sessions = new ArrayList<>();
        for (int i = 0; i < racers; i++) {
            sessions.add(CsrfSession.bootstrap(mockMvc));
        }
        String body = JSON.writeValueAsString(Map.of("token", pending.token(), "password", Registrations.PASSWORD));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        List<Future<MvcResult>> results = new ArrayList<>();
        try {
            for (CsrfSession session : sessions) {
                results.add(pool.submit(() -> {
                    start.await();
                    return mockMvc.perform(MockMvcRequestBuilders.post("/api/register/activate")
                            .with(session.inHeader()).contentType(MediaType.APPLICATION_JSON).content(body))
                            .andReturn();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            List<String> codes = new ArrayList<>();
            for (Future<MvcResult> result : results) {
                MvcResult done = result.get();
                statuses.add(done.getResponse().getStatus());
                if (done.getResponse().getStatus() != 204) {
                    codes.add(JSON.readTree(done.getResponse().getContentAsString(StandardCharsets.UTF_8))
                            .get("code").asString());
                }
            }
            assertThat(statuses).filteredOn(code -> code == 204).hasSize(1);
            assertThat(codes).hasSize(racers - 1).containsOnly(ErrorCode.RESET_TOKEN_INVALID.name());
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM credential_tokens t JOIN users u ON u.id = t.user_id"
                + " WHERE u.username = ? AND t.used_at IS NOT NULL", Integer.class, pending.username())).isOne();
    }

    private static ObjectNode withoutTraceId(MvcResult result) throws Exception {
        ObjectNode body = (ObjectNode) JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        body.remove("traceId");
        return body;
    }
}
