package sg.securedhello.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.security.ratelimit.RateLimitProperties;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CtxBudgetTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.Registrations;
import sg.securedhello.testsupport.SignedIn;

/**
 * The throttle rows on the real budgets: a source throttle (row 5) and a submitted-value throttle (row 6) never carry
 * {@code user.id}, whatever the MDC holds (Std §3.4). Row 6 carries none by construction, since the value may name no
 * account (ADR-010).
 */
class PreAuthenticationThrottleRowsTest extends CtxBudgetTest {

    private static final String PLANTED = UUID.randomUUID().toString();

    @Autowired
    private AuditEmitter emitter;

    @Autowired
    private RateLimitProperties budgets;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @Proves("T-AUD-026")
    void throttleRowsCarryNoUserIdEvenWithOneInTheMdc() throws Exception {
        String source = nextSource();
        Registrations registrations = new Registrations(mockMvc, source);
        String username = Accounts.unknownUsername();
        emitter.closeKeyingWindow();
        MDC.put("user.id", PLANTED);
        MDC.put("userId", PLANTED);

        List<Map<String, Object>> rows;
        try (AuditCapture audit = AuditCapture.start()) {
            for (long i = 0; i < budgets.register().source().burst(); i++) {
                registrations.register(Registrations.freshUsername(), "not-an-address")
                        .andExpect(problem(ErrorCode.VALIDATION_FAILED));
            }
            registrations.register(Registrations.freshUsername(), "not-an-address")
                    .andExpect(problem(ErrorCode.TOO_MANY_REQUESTS));
            for (long i = 0; i <= budgets.login().username().burst(); i++) {
                SignedIn.loginFrom(mockMvc, nextSource(), username, Accounts.WRONG_PASSWORD);
            }
            SignedIn.loginFrom(mockMvc, nextSource(), username, Accounts.WRONG_PASSWORD)
                    .andExpect(problem(ErrorCode.TOO_MANY_REQUESTS));
            emitter.closeKeyingWindow();
            rows = audit.rows();
        }

        assertThat(rows).extracting(row -> row.get("event.reason"))
                .contains("RATE_LIMITED_SOURCE", "RATE_LIMITED_IDENTIFIER");
        assertThat(rows).allSatisfy(row -> assertThat(row).doesNotContainKeys("user.id", "userId"))
                .noneMatch(row -> row.containsValue(PLANTED));
    }
}
