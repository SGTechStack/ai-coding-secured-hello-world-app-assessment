package sg.securedhello.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.mfa.TotpSecretCipher;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.CtxPortTest;
import sg.securedhello.testsupport.ProblemAssertions;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SessionCookies;
import sg.securedhello.testsupport.SessionRows;
import sg.securedhello.testsupport.SignedIn;
import sg.securedhello.testsupport.TotpFactors;

import tools.jackson.databind.json.JsonMapper;

/**
 * An admin disable, role change, delete or factor reset ends the subject's live sessions after commit (ADR-037;
 * ADR-039): the subject's raw cookie, replayed against the real JDBC session store over a real port, is refused and its
 * row is gone (level P).
 */
class AdminDisableSessionsPortTest extends CtxPortTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TotpSecretCipher cipher;

    /** A session: its cookie value and its CSRF token. */
    private record Session(String cookie, String token) {
    }

    private EntityExchangeResult<String> send(HttpMethod method, String path, Session session, String json) {
        var request = restClient.method(method).uri(URI.create("http://localhost:" + port + path));
        if (session != null) {
            request.header(HttpHeaders.COOKIE, "SESSION=" + session.cookie());
            if (session.token() != null) {
                request.header("X-CSRF-TOKEN", session.token());
            }
        }
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(json);
        }
        return request.exchange().expectBody(String.class).returnResult();
    }

    private static String cookieValue(EntityExchangeResult<?> result) {
        return SessionCookies.value(result.getResponseHeaders());
    }

    /** The session's CSRF token, fetched on it. */
    private Session withToken(String cookie) {
        EntityExchangeResult<String> csrf = send(HttpMethod.GET, "/api/csrf", new Session(cookie, null), null);
        return new Session(cookie, JSON.readTree(csrf.getResponseBody()).get("token").asString());
    }

    private Session signIn(Account account) {
        EntityExchangeResult<String> bootstrap = send(HttpMethod.GET, "/api/csrf", null, null);
        Session anonymous = new Session(cookieValue(bootstrap),
                JSON.readTree(bootstrap.getResponseBody()).get("token").asString());
        EntityExchangeResult<String> login = send(HttpMethod.POST, "/api/login", anonymous,
                SignedIn.credentials(account.username(), account.password()));
        assertThat(login.getStatus().value()).isEqualTo(200);
        return withToken(cookieValue(login));
    }

    @Test
    @Proves("T-SES-003")
    void aDisabledUsersReplayedCookieIsRefusedAndItsRowIsGone() throws Exception {
        Accounts accounts = new Accounts(jdbc, passwordEncoder);
        TotpFactors factors = new TotpFactors(jdbc, cipher, clock);
        Account target = accounts.user();
        Account admin = accounts.withRole("ADMIN");
        byte[] secret = factors.enrol(admin);
        String captured = signIn(target).cookie();
        assertThat(send(HttpMethod.GET, "/api/hello", new Session(captured, null), null).getStatus().value())
                .as("the target's session is live").isEqualTo(200);

        EntityExchangeResult<String> verified = send(HttpMethod.POST, TotpFactors.VERIFICATION, signIn(admin),
                JSON.writeValueAsString(Map.of("code", factors.code(secret))));
        assertThat(verified.getStatus().value()).isEqualTo(204);
        EntityExchangeResult<String> disabled = send(HttpMethod.PUT, "/api/admin/users/" + target.id() + "/enabled",
                withToken(cookieValue(verified)), "{\"enabled\":false}");
        assertThat(disabled.getStatus().value()).isEqualTo(200);

        assertThat(new SessionRows(jdbc).exists(SessionRows.idOf(captured))).as("the session row is gone").isFalse();
        EntityExchangeResult<String> replay = send(HttpMethod.GET, "/api/hello", new Session(captured, null), null);
        ProblemAssertions.assertProblem(replay.getStatus().value(),
                replay.getResponseHeaders().getFirst(HttpHeaders.CONTENT_TYPE), replay.getResponseBody(),
                replay.getResponseHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE), ErrorCode.AUTHENTICATION_FAILED);
    }

    @Test
    @Proves("T-SES-004")
    void aPromotedUsersReplayedCookieIsRefusedAndItsRowIsGone() {
        assertReplayRefusedAfter(HttpMethod.PUT, "/role", "{\"role\":\"ADMIN\"}", 200);
    }

    @Test
    @Proves("T-SES-016")
    void aDeletedUsersReplayedCookieIsRefusedAndItsRowIsGone() {
        assertReplayRefusedAfter(HttpMethod.DELETE, "", null, 204);
    }

    @Test
    @Proves("T-SES-015")
    void aResetSubjectsReplayedCookieIsRefusedAndItsRowIsGone() {
        assertReplayRefusedAfter(HttpMethod.POST, "/password-reset", null, 200);
    }

    @Test
    @Proves("T-SES-018")
    void aFactorResetAdminsReplayedCookieIsRefusedAndItsRowIsGone() {
        Account target = new Accounts(jdbc, passwordEncoder).withRole("ADMIN");
        new TotpFactors(jdbc, cipher, clock).enrol(target);
        assertReplayRefusedAfter(target, "/api/profile", HttpMethod.DELETE, "/totp", null, 204);
    }

    /** {@link #assertReplayRefusedAfter(Account, String, HttpMethod, String, String, int)} for a fresh user. */
    private void assertReplayRefusedAfter(HttpMethod method, String suffix, String json, int expectedStatus) {
        assertReplayRefusedAfter(new Accounts(jdbc, passwordEncoder).user(), "/api/hello", method, suffix, json,
                expectedStatus);
    }

    /**
     * Signs {@code target} in and captures its cookie, live on {@code probe}, has a verified admin send
     * {@code method} to the target's admin path plus {@code suffix}, then replays the captured cookie on
     * {@code probe}: the row is gone and the replay gets 401.
     */
    private void assertReplayRefusedAfter(Account target, String probe, HttpMethod method, String suffix,
            String json, int expectedStatus) {
        Accounts accounts = new Accounts(jdbc, passwordEncoder);
        TotpFactors factors = new TotpFactors(jdbc, cipher, clock);
        Account admin = accounts.withRole("ADMIN");
        byte[] secret = factors.enrol(admin);
        String captured = signIn(target).cookie();
        assertThat(send(HttpMethod.GET, probe, new Session(captured, null), null).getStatus().value())
                .as("the target's session is live").isEqualTo(200);

        EntityExchangeResult<String> verified = send(HttpMethod.POST, TotpFactors.VERIFICATION, signIn(admin),
                JSON.writeValueAsString(Map.of("code", factors.code(secret))));
        EntityExchangeResult<String> changed = send(method, "/api/admin/users/" + target.id() + suffix,
                withToken(cookieValue(verified)), json);
        assertThat(changed.getStatus().value()).isEqualTo(expectedStatus);

        assertThat(new SessionRows(jdbc).exists(SessionRows.idOf(captured))).as("the session row is gone").isFalse();
        EntityExchangeResult<String> replay = send(HttpMethod.GET, probe, new Session(captured, null), null);
        ProblemAssertions.assertProblem(replay.getStatus().value(),
                replay.getResponseHeaders().getFirst(HttpHeaders.CONTENT_TYPE), replay.getResponseBody(),
                replay.getResponseHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE), ErrorCode.AUTHENTICATION_FAILED);
    }
}
