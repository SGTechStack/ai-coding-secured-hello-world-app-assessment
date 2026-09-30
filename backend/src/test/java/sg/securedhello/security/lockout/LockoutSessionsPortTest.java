package sg.securedhello.security.lockout;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.CtxPortTest;
import sg.securedhello.testsupport.ProblemAssertions;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SessionRows;
import sg.securedhello.testsupport.SignedIn;

import tools.jackson.databind.json.JsonMapper;

/**
 * The failure that locks an account, or disables its password at the NIST cap, ends the account's live sessions after
 * commit (ADR-037; ADR-039), shown by replaying the owner's raw cookie against the real JDBC session store (level P).
 */
class LockoutSessionsPortTest extends CtxPortTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private LockoutProperties lockout;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private EntityExchangeResult<String> send(HttpMethod method, String path, String cookie, String token,
            String json) {
        var request = restClient.method(method).uri(URI.create("http://localhost:" + port + path));
        if (cookie != null) {
            request.header(HttpHeaders.COOKIE, "SESSION=" + cookie);
        }
        if (token != null) {
            request.header("X-CSRF-TOKEN", token);
        }
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(json);
        }
        return request.exchange().expectBody(String.class).returnResult();
    }

    private static String cookieValue(EntityExchangeResult<?> result) {
        return result.getResponseHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0].split("=", 2)[1];
    }

    /** Posts a login on a freshly bootstrapped anonymous session; returns the status. */
    private int login(Account account, String password) {
        EntityExchangeResult<String> bootstrap = send(HttpMethod.GET, "/api/csrf", null, null, null);
        EntityExchangeResult<String> login = send(HttpMethod.POST, "/api/login", cookieValue(bootstrap),
                JSON.readTree(bootstrap.getResponseBody()).get("token").asString(),
                SignedIn.credentials(account.username(), password));
        return login.getStatus().value();
    }

    /** Signs {@code account} in and returns its session cookie. */
    private String signIn(Account account) {
        EntityExchangeResult<String> bootstrap = send(HttpMethod.GET, "/api/csrf", null, null, null);
        EntityExchangeResult<String> login = send(HttpMethod.POST, "/api/login", cookieValue(bootstrap),
                JSON.readTree(bootstrap.getResponseBody()).get("token").asString(),
                SignedIn.credentials(account.username(), account.password()));
        assertThat(login.getStatus().value()).isEqualTo(200);
        return cookieValue(login);
    }

    private void assertEnded(String cookie) {
        assertThat(new SessionRows(jdbc).exists(SessionRows.idOf(cookie))).as("the session row is gone").isFalse();
        EntityExchangeResult<String> replay = send(HttpMethod.GET, "/api/hello", cookie, null, null);
        ProblemAssertions.assertProblem(replay.getStatus().value(),
                replay.getResponseHeaders().getFirst(HttpHeaders.CONTENT_TYPE), replay.getResponseBody(),
                replay.getResponseHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE), ErrorCode.AUTHENTICATION_FAILED);
    }

    @Test
    @Proves("T-SES-017")
    void theFailureThatLocksTheAccountEndsItsLiveSession() {
        Accounts accounts = new Accounts(jdbc, passwordEncoder);
        Account account = accounts.user();
        String live = signIn(account);

        for (int i = 0; i < lockout.threshold() - 1; i++) {
            assertThat(login(account, Accounts.WRONG_PASSWORD)).isEqualTo(401);
        }
        assertThat(send(HttpMethod.GET, "/api/hello", live, null, null).getStatus().value())
                .as("below the threshold the owner's session stands (ADR-034)").isEqualTo(200);

        assertThat(login(account, Accounts.WRONG_PASSWORD)).isEqualTo(401);

        assertThat(accounts.lockoutState(account).lockedUntil()).as("the account is locked").isNotNull();
        assertEnded(live);
    }

    @Test
    @Proves("T-SES-021")
    void theFailureThatReachesTheCapEndsItsLiveSession() {
        Accounts accounts = new Accounts(jdbc, passwordEncoder);
        Account account = accounts.user();
        String live = signIn(account);
        // One failure short of the cap, with no lock in force: the next failure disables the password.
        jdbc.update("UPDATE users SET consecutive_failures_since_success = ? WHERE id = ?",
                lockout.nist().cap() - 1, account.id());

        assertThat(login(account, Accounts.WRONG_PASSWORD)).isEqualTo(401);

        assertThat(accounts.lockoutState(account).passwordDisabledAt()).as("the password is disabled").isNotNull();
        assertThat(accounts.lockoutState(account).lockedUntil()).as("the cap alone ended it, not a lock").isNull();
        assertEnded(live);
    }
}
