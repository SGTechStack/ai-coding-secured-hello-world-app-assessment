package sg.securedhello.security.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.security.ratelimit.RateLimitProperties;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxBudgetTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SignedIn;

import tools.jackson.databind.json.JsonMapper;

/**
 * The per-username login budget (ADR-010; R-STD-018): charged in the converter straight after the username is read,
 * refused there with 429 before {@code ProviderManager}, identically for real and unknown usernames.
 */
class LoginThrottleTest extends CtxBudgetTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String IDENTIFIER_ROW = "Request throttled for its submitted identifier.";

    @Autowired
    private RateLimitProperties budgets;

    @Autowired
    private JdbcTemplate jdbc;

    private Accounts accounts;
    private String source;
    private CsrfSession session;

    @BeforeEach
    void setUp() throws Exception {
        accounts = new Accounts(jdbc, passwordEncoder);
        source = nextSource();
        session = CsrfSession.bootstrap(mockMvc, source);
    }

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/login").with(session.inHeader()).with(request -> {
            request.setRemoteAddr(source);
            return request;
        }).contentType(MediaType.APPLICATION_JSON).content(SignedIn.credentials(username, password)));
    }

    /** Spends {@code username}'s whole burst on wrong passwords. */
    private void spend(String username) throws Exception {
        for (long i = 0; i < budgets.login().username().burst(); i++) {
            login(username, "not-the-password-at-all").andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
        }
    }

    private int failedLoginAttempts(Account account) {
        return jdbc.queryForObject("SELECT failed_login_attempts FROM users WHERE id = ?", Integer.class,
                account.id());
    }

    @Test
    @Proves("T-RL-003")
    void pastTheUsernameBurstTheConverterRefusesBeforeTheProviderAndNoCounterMoves() throws Exception {
        Account target = accounts.user();
        try (AuditCapture audit = AuditCapture.start()) {
            spend(target.username());
            int attempts = failedLoginAttempts(target);
            clearInvocations(passwordEncoder, userAccounts);

            login(target.username(), target.password())
                    .andExpect(problem(ErrorCode.TOO_MANY_REQUESTS))
                    .andExpect(header().string(HttpHeaders.RETRY_AFTER, "6"));

            verify(passwordEncoder, never()).matches(any(), any());
            assertThat(failedLoginAttempts(target)).isEqualTo(attempts);
            assertThat(audit.withMessage("Login failed.")).as("no failure event for the refusal")
                    .hasSize((int) budgets.login().username().burst());
        }
    }

    @Test
    @Proves("T-RL-017")
    void anUnknownUsernameIsRefusedOnTheSameScheduleWithNoLookup() throws Exception {
        Account known = accounts.user();
        String unknown = Accounts.unknownUsername();
        spend(known.username());
        spend(unknown);
        clearInvocations(passwordEncoder, userAccounts);

        MvcResult knownRefusal = login(known.username(), "x").andExpect(problem(ErrorCode.TOO_MANY_REQUESTS))
                .andReturn();
        MvcResult unknownRefusal = login(unknown, "x").andExpect(problem(ErrorCode.TOO_MANY_REQUESTS))
                .andReturn();

        verify(userAccounts, never()).findByUsername(anyString());
        verify(passwordEncoder, never()).matches(any(), any());
        assertThat(unknownRefusal.getResponse().getHeader(HttpHeaders.RETRY_AFTER))
                .isEqualTo(knownRefusal.getResponse().getHeader(HttpHeaders.RETRY_AFTER));
        assertThat(withoutTraceId(unknownRefusal)).isEqualTo(withoutTraceId(knownRefusal));

        clock.advance(budgets.login().username().refillPeriod());
        login(known.username(), "x").andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
        login(unknown, "x").andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
        login(known.username(), "x").andExpect(problem(ErrorCode.TOO_MANY_REQUESTS));
        login(unknown, "x").andExpect(problem(ErrorCode.TOO_MANY_REQUESTS));
    }

    @Test
    @Proves("T-RL-002")
    void mixedTrafficReachesThe429WithTheEnvelopeRetryAfterAndNoStoreHeaders() throws Exception {
        Account user = accounts.user();
        long burst = budgets.login().username().burst();
        for (long i = 1; i <= burst; i++) {
            if (i % 5 == 0) {
                login(user.username(), user.password()).andExpect(status().isOk());
                session = CsrfSession.bootstrap(mockMvc, source);
            } else {
                login(user.username(), "not-the-password-at-all").andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
            }
        }

        login(user.username(), user.password())
                .andExpect(problem(ErrorCode.TOO_MANY_REQUESTS))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "6"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, max-age=0, must-revalidate"))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andExpect(header().string(HttpHeaders.EXPIRES, "0"));
    }

    @Test
    void theIdentifierRowIsWrittenOncePerExhaustionWithNoIdentity() throws Exception {
        String username = Accounts.unknownUsername();
        spend(username);
        try (AuditCapture audit = AuditCapture.start()) {
            login(username, "x").andExpect(problem(ErrorCode.TOO_MANY_REQUESTS));
            login(username, "x").andExpect(problem(ErrorCode.TOO_MANY_REQUESTS));
            assertThat(audit.withMessage(IDENTIFIER_ROW)).singleElement().satisfies(row -> assertThat(row)
                    .containsEntry("event.reason", "RATE_LIMITED_IDENTIFIER")
                    .containsEntry("log.level", "WARN")
                    .doesNotContainKey("user.id")
                    .satisfies(fields -> assertThat(fields.values().toString()).doesNotContain(username)));

            clock.advance(budgets.login().username().refillPeriod());
            login(username, "x").andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
            login(username, "x").andExpect(problem(ErrorCode.TOO_MANY_REQUESTS));
            assertThat(audit.withMessage(IDENTIFIER_ROW)).hasSize(2);
        }
    }

    private static Map<String, Object> withoutTraceId(MvcResult result) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> body = JSON.readValue(result.getResponse().getContentAsString(), Map.class);
        body.remove("traceId");
        return body;
    }
}
