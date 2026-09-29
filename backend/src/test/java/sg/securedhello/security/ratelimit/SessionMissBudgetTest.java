package sg.securedhello.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.error.ErrorCode;
import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxBudgetTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SessionRows;

/**
 * The per-source session-miss budget (ADR-017): every request that presents a session id the store cannot resolve
 * costs its source one miss, on any route, and a source that has spent its budget is refused before the lookup. The
 * route used here, {@code /actuator/health}, is absent from the budget table.
 */
class SessionMissBudgetTest extends CtxBudgetTest {

    private static final String UNLISTED = "/actuator/health";

    @Autowired
    private RateLimitProperties budgets;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AuditEmitter auditEmitter;

    private SessionRows sessions;

    @BeforeEach
    void setUp() {
        sessions = new SessionRows(jdbc);
    }

    /** A {@code SESSION} cookie naming a session the store has never held. */
    private static Cookie staleCookie() {
        return new Cookie("SESSION", Base64.getEncoder().encodeToString(UUID.randomUUID().toString()
                .getBytes(StandardCharsets.UTF_8)));
    }

    private ResultActions withStaleCookie(String source) throws Exception {
        return mockMvc.perform(get(UNLISTED).cookie(staleCookie()).with(request -> {
            request.setRemoteAddr(source);
            return request;
        }));
    }

    private void spendTheMissBudget(String source) throws Exception {
        spendMisses(source, budgets.sessionMiss().capacity());
    }

    private void spendMisses(String source, long misses) throws Exception {
        for (long i = 0; i < misses; i++) {
            withStaleCookie(source).andExpect(status().isOk());
        }
    }

    @Test
    @Proves("T-RL-016")
    void theMissAfterTheCapacityIsRefusedOnAnUnlistedRouteAndCreatesNoSession() throws Exception {
        String source = nextSource();
        spendTheMissBudget(source);
        Set<String> before = sessions.ids();

        MvcResult refused = withStaleCookie(source)
                .andExpect(problem(ErrorCode.TOO_MANY_REQUESTS))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER,
                        Long.toString(budgets.sessionMiss().window().toSeconds())))
                .andReturn();

        assertThat(sessions.ids()).isEqualTo(before);
        assertThat(refused.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        mockMvc.perform(get(UNLISTED).with(request -> {
            request.setRemoteAddr(source);
            return request;
        })).andExpect(status().isOk());
    }

    @Test
    @Proves("T-RL-022")
    void aSpentMissBudgetIsRestoredWhenTheBoundWindowHasPassed() throws Exception {
        Duration window = budgets.sessionMiss().window();
        String source = nextSource();
        spendTheMissBudget(source);

        clock.advance(window.minusMillis(1));
        withStaleCookie(source).andExpect(problem(ErrorCode.TOO_MANY_REQUESTS));
        clock.advance(Duration.ofMillis(1));
        withStaleCookie(source).andExpect(status().isOk());
    }

    @Test
    @Proves("T-RL-021")
    void aMissCostsOneSessionLookupAndTheRefusalCostsNone() throws Exception {
        String source = nextSource();
        clearInvocations(sessionRepository);

        withStaleCookie(source).andExpect(status().isOk());
        verify(sessionRepository, times(1)).findById(anyString());

        spendMisses(source, budgets.sessionMiss().capacity() - 1);
        clearInvocations(sessionRepository);
        withStaleCookie(source).andExpect(problem(ErrorCode.TOO_MANY_REQUESTS));
        verify(sessionRepository, never()).findById(anyString());
    }

    @Test
    @Proves("T-RL-023")
    void theRefusalWritesTheMissThrottleRowOnlyAndCreatesNoSession() throws Exception {
        String source = nextSource();
        spendTheMissBudget(source);
        auditEmitter.closeKeyingWindow();
        Set<String> before = sessions.ids();

        try (AuditCapture audit = AuditCapture.start()) {
            withStaleCookie(source).andExpect(problem(ErrorCode.TOO_MANY_REQUESTS));
            withStaleCookie(source).andExpect(problem(ErrorCode.TOO_MANY_REQUESTS));
            auditEmitter.closeKeyingWindow();

            assertThat(audit.rows()).singleElement().satisfies(row -> assertThat(row)
                    .containsEntry("message", "Request throttled for its source.")
                    .containsEntry("event.reason", "RATE_LIMITED_SOURCE_MISSES")
                    .containsEntry("event.count", 2)
                    .doesNotContainKeys("user.id", "session.hash"));
        }
        assertThat(sessions.ids()).isEqualTo(before);
    }

    @Test
    void aLiveSessionCostsNoMissAndAnotherSourceIsUnaffected() throws Exception {
        String source = nextSource();
        CsrfSession live = CsrfSession.bootstrap(mockMvc, source);
        for (long i = 0; i < budgets.sessionMiss().capacity() + 1; i++) {
            mockMvc.perform(get(UNLISTED).cookie(live.cookie()).with(request -> {
                request.setRemoteAddr(source);
                return request;
            })).andExpect(status().isOk());
        }
        spendTheMissBudget(source);

        withStaleCookie(nextSource()).andExpect(status().isOk());
    }
}
