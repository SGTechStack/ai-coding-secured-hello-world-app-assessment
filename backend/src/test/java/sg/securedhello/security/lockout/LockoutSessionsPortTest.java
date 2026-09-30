package sg.securedhello.security.lockout;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

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
import sg.securedhello.testsupport.SessionCookies;
import sg.securedhello.testsupport.SessionRows;
import sg.securedhello.testsupport.SignedIn;

import tools.jackson.databind.json.JsonMapper;

/**
 * The failure that locks a trusted device, or disables the password at the NIST cap, ends the account's live sessions
 * after commit (ADR-037; ADR-039); the failure that locks only the untrusted lane ends none (ADR-075). Shown by
 * replaying the owner's raw cookie against the real JDBC session store (level P).
 */
class LockoutSessionsPortTest extends CtxPortTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private LockoutProperties lockout;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /** One request, with the session cookie {@code cookie} and, if not {@code null}, the {@code device} pair. */
    private EntityExchangeResult<String> send(HttpMethod method, String path, String cookie, String token,
            String json, String device) {
        var request = restClient.method(method).uri(URI.create("http://localhost:" + port + path));
        List<String> cookies = new ArrayList<>();
        if (cookie != null) {
            cookies.add("SESSION=" + cookie);
        }
        if (device != null) {
            cookies.add(device);
        }
        if (!cookies.isEmpty()) {
            request.header(HttpHeaders.COOKIE, String.join("; ", cookies));
        }
        if (token != null) {
            request.header("X-CSRF-TOKEN", token);
        }
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(json);
        }
        return request.exchange().expectBody(String.class).returnResult();
    }

    private EntityExchangeResult<String> send(HttpMethod method, String path, String cookie, String token,
            String json) {
        return send(method, path, cookie, token, json, null);
    }

    private static String cookieValue(EntityExchangeResult<?> result) {
        return SessionCookies.value(result.getResponseHeaders());
    }

    /** Posts a login on a freshly bootstrapped anonymous session, presenting {@code device} if any. */
    private EntityExchangeResult<String> post(Account account, String password, String device) {
        EntityExchangeResult<String> bootstrap = send(HttpMethod.GET, "/api/csrf", null, null, null);
        return send(HttpMethod.POST, "/api/login", cookieValue(bootstrap),
                JSON.readTree(bootstrap.getResponseBody()).get("token").asString(),
                SignedIn.credentials(account.username(), password), device);
    }

    /** Posts a login with no device cookie; returns the status. */
    private int login(Account account, String password) {
        return post(account, password, null).getStatus().value();
    }

    /** A signed-in session, and the device cookie its sign-in earned as a {@code name=value} pair. */
    private record Signed(String session, String device) {
    }

    /** Signs {@code account} in with no device cookie; returns its session cookie and the device cookie it earned. */
    private Signed signIn(Account account) {
        EntityExchangeResult<String> login = post(account, account.password(), null);
        assertThat(login.getStatus().value()).isEqualTo(200);
        String device = login.getResponseHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).stream()
                .map(header -> header.split(";", 2)[0]).filter(SessionCookies::isDevice).findFirst().orElseThrow();
        return new Signed(cookieValue(login), device);
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
    void theFailureThatLocksATrustedDeviceEndsTheAccountsLiveSession() {
        Accounts accounts = new Accounts(jdbc, passwordEncoder);
        Account account = accounts.user();
        Signed live = signIn(account);

        for (int i = 0; i < lockout.threshold() - 1; i++) {
            assertThat(post(account, Accounts.WRONG_PASSWORD, live.device()).getStatus().value()).isEqualTo(401);
        }
        assertThat(send(HttpMethod.GET, "/api/hello", live.session(), null, null).getStatus().value())
                .as("below the threshold the owner's session stands (ADR-034)").isEqualTo(200);

        assertThat(post(account, Accounts.WRONG_PASSWORD, live.device()).getStatus().value()).isEqualTo(401);

        assertThat(jdbc.queryForObject("SELECT locked_until FROM trusted_devices WHERE user_id = ?",
                Timestamp.class, account.id())).as("the device is locked").isNotNull();
        assertEnded(live.session());
    }

    @Test
    @Proves("T-SES-038")
    void theFailureThatLocksOnlyTheUntrustedLaneLeavesTheAccountsLiveSessionAlone() {
        Accounts accounts = new Accounts(jdbc, passwordEncoder);
        Account account = accounts.user();
        Signed live = signIn(account);

        for (int i = 0; i < lockout.threshold(); i++) {
            assertThat(login(account, Accounts.WRONG_PASSWORD)).isEqualTo(401);
        }

        assertThat(accounts.lockoutState(account).lockedUntil()).as("the untrusted lane is locked").isNotNull();
        assertThat(new SessionRows(jdbc).exists(SessionRows.idOf(live.session()))).as("the session row stands")
                .isTrue();
        assertThat(send(HttpMethod.GET, "/api/hello", live.session(), null, null).getStatus().value())
                .as("the owner's session still answers (ADR-075)").isEqualTo(200);
    }

    @Test
    @Proves("T-SES-021")
    void theFailureThatReachesTheCapEndsItsLiveSession() {
        Accounts accounts = new Accounts(jdbc, passwordEncoder);
        Account account = accounts.user();
        String live = signIn(account).session();
        // One failure short of the cap, with no lock in force: the next failure disables the password.
        jdbc.update("UPDATE users SET consecutive_failures_since_success = ? WHERE id = ?",
                lockout.nist().cap() - 1, account.id());

        assertThat(login(account, Accounts.WRONG_PASSWORD)).isEqualTo(401);

        assertThat(accounts.lockoutState(account).passwordDisabledAt()).as("the password is disabled").isNotNull();
        assertThat(accounts.lockoutState(account).lockedUntil()).as("the cap alone ended it, not a lock").isNull();
        assertEnded(live);
    }
}
