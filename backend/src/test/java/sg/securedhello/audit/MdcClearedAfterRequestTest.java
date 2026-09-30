package sg.securedhello.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SignedIn;

/**
 * Nothing a request puts in the MDC outlives it on the thread that ran it (LOG §5:354). MockMvc runs the whole
 * filter chain on the test's own thread, so the thread's MDC is read directly once each request returns.
 */
class MdcClearedAfterRequestTest extends CtxDefaultTest {

    private static final List<String> REQUEST_KEYS = List.of("traceId", "spanId", "trace.id", "span.id", "user.id",
            "session.hash");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static void assertNoRequestStateLeft(String after) {
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        assertThat(mdc == null ? Map.of() : mdc).as("MDC after %s", after).doesNotContainKeys(
                REQUEST_KEYS.toArray(String[]::new));
    }

    @Test
    @Proves("T-AUD-004")
    void theMdcHoldsNoUserSessionOrTraceFieldsOnceARequestReturns() throws Exception {
        Account account = new Accounts(jdbc, passwordEncoder).user();
        MDC.clear();

        try (AuditCapture audit = AuditCapture.start()) {
            CsrfSession session = SignedIn.as(mockMvc, account);
            assertNoRequestStateLeft("sign-in");
            mockMvc.perform(get("/api/profile").cookie(session.cookie()));
            assertNoRequestStateLeft("an authenticated read");
            mockMvc.perform(get("/api/admin/users").cookie(session.cookie()));
            assertNoRequestStateLeft("a refused request");
            mockMvc.perform(get("/api/no-such-route"));
            assertNoRequestStateLeft("an anonymous error");

            // Not vacuous: while the sign-in ran, its rows did carry the trace from the MDC.
            assertThat(audit.withMessage("Login succeeded.")).singleElement()
                    .satisfies(row -> assertThat(row).containsKeys("trace.id", "span.id"));
        }
    }
}
