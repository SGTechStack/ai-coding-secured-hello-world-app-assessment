package sg.securedhello.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.sql.Timestamp;
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
 * A credential change ends the account's other sessions and keeps the acting one under a new id (ADR-035; ADR-037;
 * ADR-038), shown with raw cookies replayed against the real JDBC session store (level P).
 *
 * <p>One session per account means the second sign-in displaces the first; the displaced session's row lingers until
 * something removes it (eviction is lazy). The change must remove it.
 */
class PasswordChangeSessionsPortTest extends CtxPortTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String NEW_PASSWORD = "velvet harbour quietly hums";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /** A signed-in session: its cookie value and its current CSRF token. */
    private record Signed(String cookie, String token) {
    }

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
        return SessionCookies.value(result.getResponseHeaders());
    }

    private String token(String cookie) {
        return JSON.readTree(send(HttpMethod.GET, "/api/csrf", cookie, null, null).getResponseBody()).get("token")
                .asString();
    }

    private Signed signIn(Account account) {
        EntityExchangeResult<String> bootstrap = send(HttpMethod.GET, "/api/csrf", null, null, null);
        EntityExchangeResult<String> login = send(HttpMethod.POST, "/api/login", cookieValue(bootstrap),
                JSON.readTree(bootstrap.getResponseBody()).get("token").asString(),
                SignedIn.credentials(account.username(), account.password()));
        assertThat(login.getStatus().value()).isEqualTo(200);
        String cookie = cookieValue(login);
        return new Signed(cookie, token(cookie));
    }

    /** Signs in twice, changes the password on the second session, and checks both sessions' fate. */
    private void changeEndsTheOtherSessionAndKeepsTheActingOne(Account account) {
        SessionRows rows = new SessionRows(jdbc);
        Signed other = signIn(account);
        Signed acting = signIn(account);
        assertThat(rows.exists(SessionRows.idOf(other.cookie()))).as("the displaced session's row lingers").isTrue();

        EntityExchangeResult<String> change = send(HttpMethod.PATCH, "/api/profile/password", acting.cookie(),
                acting.token(), JSON.writeValueAsString(Map.of("currentPassword", account.password(),
                        "newPassword", NEW_PASSWORD)));
        assertThat(change.getStatus().value()).isEqualTo(204);
        String rotated = cookieValue(change);

        // The other session: its replayed cookie is refused and its row is gone.
        assertThat(rows.exists(SessionRows.idOf(other.cookie()))).isFalse();
        EntityExchangeResult<String> replay = send(HttpMethod.GET, "/api/hello", other.cookie(), null, null);
        ProblemAssertions.assertProblem(replay.getStatus().value(),
                replay.getResponseHeaders().getFirst(HttpHeaders.CONTENT_TYPE), replay.getResponseBody(),
                replay.getResponseHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE), ErrorCode.AUTHENTICATION_FAILED);

        // The acting session survives under a new id; the old id is no longer a session.
        assertThat(rotated).isNotEqualTo(acting.cookie());
        assertThat(rows.exists(SessionRows.idOf(acting.cookie()))).isFalse();
        assertThat(rows.exists(SessionRows.idOf(rotated))).isTrue();
        assertThat(send(HttpMethod.GET, "/api/hello", rotated, null, null).getStatus().value()).isEqualTo(200);
        assertThat(token(rotated)).as("the CSRF token rotates with the id").isNotEqualTo(acting.token());
    }

    @Test
    @Proves("T-SES-012")
    void aSelfServiceChangeEndsTheOtherSessionsAndRotatesTheActingOne() {
        changeEndsTheOtherSessionAndKeepsTheActingOne(new Accounts(jdbc, passwordEncoder).user());
    }

    @Test
    @Proves("T-SES-013")
    void completingAForcedChangeEndsTheOtherSessionsAndRotatesTheActingOne() {
        Account account = new Accounts(jdbc, passwordEncoder).user();
        jdbc.update("UPDATE users SET force_password_change = TRUE, credential_issued_at = ? WHERE id = ?",
                Timestamp.from(clock.instant()), account.id());

        changeEndsTheOtherSessionAndKeepsTheActingOne(account);

        assertThat(jdbc.queryForObject("SELECT force_password_change FROM users WHERE id = ?", Boolean.class,
                account.id())).isFalse();
    }
}
