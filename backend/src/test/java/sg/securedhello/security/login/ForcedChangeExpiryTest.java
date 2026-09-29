package sg.securedhello.security.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxBudgetTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SignedIn;
import sg.securedhello.user.PasswordLockoutState;
import sg.securedhello.user.UserAccount;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The lazy 30-day forced-change expiry (ADR-046): refused at sign-in by the pre-authentication checks, so a correct and
 * a wrong password are indistinguishable on the wire, in the audit row, in the lockout counters and in the hashing
 * work. It runs on {@link CtxBudgetTest} for the spied encoder, each login from its own source address.
 */
class ForcedChangeExpiryTest extends CtxBudgetTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String LOGIN_FAILED = "Login failed.";
    private static final String WRONG = "not-the-password-at-all";

    @Autowired
    private JdbcTemplate jdbc;

    private Accounts accounts;

    @BeforeEach
    void setUp() {
        accounts = new Accounts(jdbc, passwordEncoder);
    }

    /** A {@code USER} account holding a forced-change credential issued at {@code issuedAt}. */
    private Accounts.Account forcedChange(Instant issuedAt) {
        Accounts.Account account = accounts.user();
        jdbc.update("UPDATE users SET force_password_change = TRUE, credential_issued_at = ? WHERE id = ?",
                Timestamp.from(issuedAt), account.id());
        return account;
    }

    /** One sign-in's outcome: the status, the body without its traceId, the cookie, and its {@code matches()} calls. */
    private record Attempt(int status, String body, Cookie cookie, long matchesCalls) {
    }

    private Attempt login(Accounts.Account account, String password) throws Exception {
        CsrfSession session = CsrfSession.bootstrap(mockMvc, nextSource());
        clearInvocations(passwordEncoder);
        MvcResult result = SignedIn.login(mockMvc, session, account.username(), password).andReturn();
        long calls = mockingDetails(passwordEncoder).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("matches"))
                .count();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        if (result.getResponse().getStatus() == 401) {
            ObjectNode problem = (ObjectNode) JSON.readTree(body);
            problem.remove("traceId");
            body = problem.toString();
        }
        Cookie cookie = result.getResponse().getCookie("SESSION");
        return new Attempt(result.getResponse().getStatus(), body, cookie == null ? session.cookie() : cookie, calls);
    }

    @Test
    @Proves("T-ADM-015")
    void underThirtyDaysTheCredentialSignsInAndIsConfinedToTheAllowlist() throws Exception {
        Accounts.Account account = forcedChange(
                clock.instant().minus(UserAccount.FORCED_CHANGE_GRACE).plusMillis(1));

        Attempt attempt = login(account, account.password());

        assertThat(attempt.status()).as("just under 30 days old still works").isEqualTo(200);
        mockMvc.perform(get("/api/hello").cookie(attempt.cookie()).with(request -> {
            request.setRemoteAddr(nextSource());
            return request;
        })).andExpect(problem(ErrorCode.PASSWORD_CHANGE_REQUIRED));
    }

    @Test
    @Proves("T-ADM-015")
    void pastThirtyDaysACorrectAndAWrongPasswordGetTheIdenticalRefusal() throws Exception {
        Accounts.Account account = forcedChange(
                clock.instant().minus(UserAccount.FORCED_CHANGE_GRACE).minus(Duration.ofSeconds(1)));
        PasswordLockoutState before = accounts.lockoutState(account);

        try (AuditCapture audit = AuditCapture.start()) {
            Attempt correct = login(account, account.password());
            Attempt wrong = login(account, WRONG);

            assertThat(correct.status()).isEqualTo(401);
            assertThat(correct.body()).as("the body without traceId").isEqualTo(wrong.body());
            mockMvc.perform(get("/api/hello").cookie(correct.cookie()).with(request -> {
                request.setRemoteAddr(nextSource());
                return request;
            })).andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
            assertThat(correct.matchesCalls()).as("correct password: one matches()").isEqualTo(1);
            assertThat(wrong.matchesCalls()).as("wrong password: one matches()").isEqualTo(1);
            assertThat(audit.withMessage(LOGIN_FAILED)).hasSize(2).allSatisfy(row -> assertThat(row)
                    .containsEntry("event.reason", "CREDENTIAL_EXPIRED")
                    .containsEntry("user.id", account.id().toString()));
        }
        assertThat(accounts.lockoutState(account)).as("neither counter moves").isEqualTo(before);
    }

    @Test
    void aNullIssueTimeNeverExpires() throws Exception {
        Accounts.Account account = accounts.user();
        jdbc.update("UPDATE users SET force_password_change = TRUE WHERE id = ?", account.id());

        Attempt attempt = login(account, account.password());

        assertThat(attempt.status()).isEqualTo(200);
        assertThat(JSON.readValue(attempt.body(), Map.class)).containsEntry("passwordChangeRequired", true);
    }

    @Test
    void aSignInReportsWhetherTheSessionMustChangeItsPassword() throws Exception {
        Accounts.Account plain = accounts.user();

        CsrfSession session = CsrfSession.bootstrap(mockMvc, nextSource());
        SignedIn.login(mockMvc, session, plain.username(), plain.password()).andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordChangeRequired").value(false));
    }
}
