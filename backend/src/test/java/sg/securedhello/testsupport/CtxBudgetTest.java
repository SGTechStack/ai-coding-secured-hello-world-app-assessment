package sg.securedhello.testsupport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import sg.securedhello.user.UserAccountRepository;

/**
 * {@code ctx-budget}: {@code ctx-default} on {@code application.yml}'s rate-limit budgets, for the tests of the budgets
 * themselves (ADR-010; ADR-017). The other shared contexts raise the budgets ({@link #HARNESS_BUDGETS}); this one does
 * not load that file, which is its stated reason to exist.
 *
 * <p>The limiter's state lives as long as the context, so every test here isolates itself by key: its own source
 * address ({@link #nextSource()}) and its own usernames. The password encoder, the account repository and the session
 * repository are spies, so a test can show a refused request cost no hash, no account lookup and no extra session
 * query; clear their invocations before the requests under test.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class CtxBudgetTest extends SharedContextTest {

    private static int nextSource;

    @Autowired
    protected MockMvc mockMvc;

    @MockitoSpyBean
    protected PasswordEncoder passwordEncoder;

    @MockitoSpyBean
    protected UserAccountRepository userAccounts;

    @MockitoSpyBean
    protected JdbcIndexedSessionRepository sessionRepository;

    /** A source address no other test in this context has used: 198.18.0.0/15, the benchmarking range. */
    protected static synchronized String nextSource() {
        nextSource++;
        return "198.18." + (nextSource / 250) + "." + (nextSource % 250 + 1);
    }
}
