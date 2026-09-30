package sg.securedhello.security.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import sg.securedhello.security.lockout.LockoutProperties;
import sg.securedhello.security.ratelimit.LockoutCardinalityProperties;
import sg.securedhello.security.ratelimit.RateLimitProperties;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxBudgetTest;
import sg.securedhello.testsupport.DeviceCookies;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SignedIn;

/**
 * Timing uniformity on the password axis, verified by mechanism rather than by stopwatch (ADR-001; R-AUTH-004;
 * REJ-056): every sign-in that reaches the provider costs exactly one {@code matches()}, whether or not the account
 * exists, is disabled or was never activated.
 *
 * <p>It runs on {@link CtxBudgetTest}: counting calls needs the encoder bean wrapped in a spy, which a shared
 * context must not have, and the limiter cases need {@code application.yml}'s budgets. A limiter refusal, the
 * lockout-cardinality axis's included, costs no {@code matches()} at all. A locked or capped account costs one, like
 * every other account, in every lockout lane (ADR-075): the lane's lock or the pre-authentication checks refuse it,
 * and the provider still compares the password.
 */
class PasswordMatchCountTest extends CtxBudgetTest {

    @Autowired
    private RateLimitProperties budgets;

    @Autowired
    private LockoutCardinalityProperties cardinality;

    @Autowired
    private LockoutProperties lockout;

    @Autowired
    private JdbcTemplate jdbc;

    private Accounts accounts;

    @BeforeEach
    void setUp() {
        accounts = new Accounts(jdbc, passwordEncoder);
    }

    private long matchesCalls(String username, String password) throws Exception {
        CsrfSession session = CsrfSession.bootstrap(mockMvc, nextSource());
        clearInvocations(passwordEncoder);
        SignedIn.login(mockMvc, session, username, password);
        return matchesCalls();
    }

    private long matchesCalls() {
        return mockingDetails(passwordEncoder).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("matches"))
                .count();
    }

    @Test
    @Proves("T-AUTH-003")
    void matchesRunsOncePerLoginWhetherOrNotTheAccountExists() throws Exception {
        Accounts.Account known = accounts.user();
        Accounts.Account locked = accounts.user();
        jdbc.update("UPDATE users SET locked_until = ? WHERE id = ?",
                Timestamp.from(clock.instant().plus(Duration.ofHours(1))), locked.id());
        Accounts.Account capped = accounts.user();
        jdbc.update("UPDATE users SET password_disabled_at = ? WHERE id = ?", Timestamp.from(clock.instant()),
                capped.id());

        assertThat(matchesCalls(known.username(), known.password())).as("success").isEqualTo(1);
        assertThat(matchesCalls(known.username(), Accounts.WRONG_PASSWORD)).as("wrong password").isEqualTo(1);
        assertThat(matchesCalls(Accounts.unknownUsername(), Accounts.PASSWORD)).as("unknown user").isEqualTo(1);
        assertThat(matchesCalls(accounts.disabled().username(), Accounts.PASSWORD)).as("disabled").isEqualTo(1);
        assertThat(matchesCalls(accounts.notActivated().username(), Accounts.PASSWORD)).as("never activated")
                .isEqualTo(1);
        assertThat(matchesCalls(locked.username(), Accounts.PASSWORD)).as("locked, right password").isEqualTo(1);
        assertThat(matchesCalls(locked.username(), Accounts.WRONG_PASSWORD)).as("locked, wrong password")
                .isEqualTo(1);
        assertThat(matchesCalls(capped.username(), Accounts.PASSWORD)).as("capped, right password").isEqualTo(1);
        assertThat(matchesCalls(capped.username(), Accounts.WRONG_PASSWORD)).as("capped, wrong password")
                .isEqualTo(1);
    }

    /**
     * The {@code matches()} calls of one sign-in presenting {@code device}, after checking it took the branch meant:
     * {@code 200} only where the lane lets the right password in.
     */
    private long matchesCalls(Cookie device, String username, String password, int expectedStatus) throws Exception {
        clearInvocations(passwordEncoder);
        int status = DeviceCookies.login(mockMvc, nextSource(), device, username, password).getResponse().getStatus();
        assertThat(status).as("status").isEqualTo(expectedStatus);
        return matchesCalls();
    }

    private void lock(String table, UUID id) {
        jdbc.update("UPDATE " + table + " SET locked_until = ? WHERE id = ?",
                Timestamp.from(clock.instant().plus(Duration.ofHours(1))), id);
    }

    /**
     * Every lane branch of ADR-075 costs one {@code matches()}, as every other sign-in does: the untrusted lane's
     * lock, a trusted device past it, a device's own lock, a cookie that does not trust (tampered, another user's) and
     * the cap on a trusted device.
     */
    @Test
    @Proves("T-AUTH-003")
    void matchesRunsOncePerLoginOnEveryLockoutLane() throws Exception {
        Accounts.Account owner = accounts.user();
        Cookie trusted = DeviceCookies.earn(mockMvc, nextSource(), owner);
        lock("users", owner.id());
        assertThat(matchesCalls(null, owner.username(), owner.password(), 401))
                .as("untrusted lane locked, right password").isEqualTo(1);
        assertThat(matchesCalls(null, owner.username(), Accounts.WRONG_PASSWORD, 401))
                .as("untrusted lane locked, wrong password").isEqualTo(1);
        assertThat(matchesCalls(trusted, owner.username(), Accounts.WRONG_PASSWORD, 401))
                .as("trusted device past an untrusted lock, wrong password").isEqualTo(1);
        assertThat(matchesCalls(trusted, owner.username(), owner.password(), 200))
                .as("trusted device past an untrusted lock, right password").isEqualTo(1);

        String value = trusted.getValue();
        char flipped = value.charAt(value.length() - 2) == 'A' ? 'B' : 'A';
        Cookie tampered = new Cookie(DeviceCookies.NAME, value.substring(0, value.length() - 2) + flipped
                + value.charAt(value.length() - 1));
        assertThat(matchesCalls(tampered, owner.username(), owner.password(), 401))
                .as("tampered cookie, untrusted lane locked").isEqualTo(1);
        Accounts.Account other = accounts.user();
        lock("users", other.id());
        assertThat(matchesCalls(trusted, other.username(), other.password(), 401))
                .as("another user's cookie, untrusted lane locked").isEqualTo(1);

        Accounts.Account deviceLocked = accounts.user();
        Cookie laptop = DeviceCookies.earn(mockMvc, nextSource(), deviceLocked);
        lock("trusted_devices", DeviceCookies.idOf(laptop));
        assertThat(matchesCalls(laptop, deviceLocked.username(), deviceLocked.password(), 401))
                .as("device lane locked, right password").isEqualTo(1);
        assertThat(matchesCalls(laptop, deviceLocked.username(), Accounts.WRONG_PASSWORD, 401))
                .as("device lane locked, wrong password").isEqualTo(1);

        Accounts.Account capped = accounts.user();
        Cookie capDevice = DeviceCookies.earn(mockMvc, nextSource(), capped);
        jdbc.update("UPDATE users SET password_disabled_at = ? WHERE id = ?", Timestamp.from(clock.instant()),
                capped.id());
        assertThat(matchesCalls(capDevice, capped.username(), capped.password(), 401))
                .as("capped, trusted device, right password").isEqualTo(1);
        assertThat(matchesCalls(capDevice, capped.username(), Accounts.WRONG_PASSWORD, 401))
                .as("capped, trusted device, wrong password").isEqualTo(1);
    }

    @Test
    @Proves("T-AUTH-003")
    void aSourceRefusalCostsNoMatches() throws Exception {
        String source = nextSource();
        CsrfSession session = CsrfSession.bootstrap(mockMvc, source);
        for (long i = 0; i < budgets.login().source().burst(); i++) {
            loginFrom(source, session, Accounts.unknownUsername()).andExpect(status().isUnauthorized());
        }
        clearInvocations(passwordEncoder);

        loginFrom(source, session, Accounts.unknownUsername()).andExpect(status().isTooManyRequests());

        assertThat(matchesCalls()).isZero();
    }

    @Test
    @Proves("T-AUTH-003")
    void aUsernameRefusalCostsNoMatches() throws Exception {
        String source = nextSource();
        CsrfSession session = CsrfSession.bootstrap(mockMvc, source);
        Accounts.Account known = accounts.user();
        for (long i = 0; i < budgets.login().username().burst(); i++) {
            loginFrom(source, session, known.username()).andExpect(status().isUnauthorized());
        }
        clearInvocations(passwordEncoder);

        loginFrom(source, session, known.username()).andExpect(status().isTooManyRequests());

        assertThat(matchesCalls()).isZero();
    }

    @Test
    @Proves("T-AUTH-003")
    void aLockoutCardinalityRefusalCostsNoMatches() throws Exception {
        String source = nextSource();
        CsrfSession session = CsrfSession.bootstrap(mockMvc, source);
        for (int i = 0; i < cardinality.k(); i++) {
            Accounts.Account victim = accounts.user();
            for (int attempt = 0; attempt < lockout.threshold(); attempt++) {
                loginFrom(source, session, victim.username()).andExpect(status().isUnauthorized());
            }
        }
        clearInvocations(passwordEncoder);

        loginFrom(source, session, accounts.user().username()).andExpect(status().isTooManyRequests());

        assertThat(matchesCalls()).isZero();
    }

    private ResultActions loginFrom(String source, CsrfSession session, String username) throws Exception {
        return mockMvc.perform(post("/api/login").with(session.inHeader()).with(request -> {
            request.setRemoteAddr(source);
            return request;
        }).contentType(MediaType.APPLICATION_JSON).content(SignedIn.credentials(username, "not-the-password")));
    }
}
