package sg.securedhello.security.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.SignedIn;

/**
 * Timing uniformity on the password axis, verified by mechanism rather than by stopwatch (ADR-001; R-AUTH-004;
 * REJ-056): every sign-in that reaches the provider costs exactly one {@code matches()}, whether or not the account
 * exists, is disabled or was never activated.
 *
 * <p>This class starts its own context: counting calls needs the encoder bean wrapped in a spy, and a spy in the
 * shared context would count every other test's hashing too. The limiter and lockout rows of T-AUTH-003 extend it
 * when those controls land.
 */
class PasswordMatchCountTest extends CtxDefaultTest {

    @MockitoSpyBean
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    private Accounts accounts;

    @BeforeEach
    void setUp() {
        accounts = new Accounts(jdbc, passwordEncoder);
    }

    private long matchesCalls(String username, String password) throws Exception {
        CsrfSession session = CsrfSession.bootstrap(mockMvc);
        clearInvocations(passwordEncoder);
        SignedIn.login(mockMvc, session, username, password);
        return mockingDetails(passwordEncoder).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("matches"))
                .count();
    }

    @Test
    void matchesRunsOncePerLoginWhetherOrNotTheAccountExists() throws Exception {
        Accounts.Account known = accounts.user();

        assertThat(matchesCalls(known.username(), known.password())).as("success").isEqualTo(1);
        assertThat(matchesCalls(known.username(), "not-the-password-at-all")).as("wrong password").isEqualTo(1);
        assertThat(matchesCalls(Accounts.unknownUsername(), Accounts.PASSWORD)).as("unknown user").isEqualTo(1);
        assertThat(matchesCalls(accounts.disabled().username(), Accounts.PASSWORD)).as("disabled").isEqualTo(1);
        assertThat(matchesCalls(accounts.notActivated().username(), Accounts.PASSWORD)).as("never activated")
                .isEqualTo(1);
    }
}
