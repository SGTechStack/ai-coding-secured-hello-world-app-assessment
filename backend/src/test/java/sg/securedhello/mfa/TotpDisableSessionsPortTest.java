package sg.securedhello.mfa;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Map;

import org.jspecify.annotations.Nullable;
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
import sg.securedhello.testsupport.TotpFactors;

import tools.jackson.databind.json.JsonMapper;

/**
 * The tier-2 trip ends every session of the subject after commit (ADR-027; ADR-037; ADR-039), shown with raw cookies
 * replayed against the real JDBC session store (level P).
 */
class TotpDisableSessionsPortTest extends CtxPortTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TotpSecretCipher cipher;

    /** A signed-in session: its cookie value and its current CSRF token. */
    private record Signed(String cookie, String token) {
    }

    private EntityExchangeResult<String> send(HttpMethod method, String path, @Nullable String cookie,
            @Nullable String token, @Nullable String json) {
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

    private static void assertProblem(EntityExchangeResult<String> result, ErrorCode code) {
        ProblemAssertions.assertProblem(result.getStatus().value(),
                result.getResponseHeaders().getFirst(HttpHeaders.CONTENT_TYPE), result.getResponseBody(),
                result.getResponseHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE), code);
    }

    @Test
    @Proves("T-MFA-023")
    void theTierTwoTripForcesAPasswordChangeAndEndsEverySessionOfTheSubject() {
        Account admin = new Accounts(jdbc, passwordEncoder).withRole("ADMIN");
        TotpFactors factors = new TotpFactors(jdbc, cipher, clock);
        byte[] secret = factors.enrol(admin);
        jdbc.update("UPDATE totp_user_details SET cumulative_failures = ? WHERE user_id = ?",
                TotpUserDetails.DISABLE_THRESHOLD - 1, admin.id());
        SessionRows rows = new SessionRows(jdbc);
        Signed other = signIn(admin);
        Signed acting = signIn(admin);
        assertThat(rows.exists(SessionRows.idOf(other.cookie()))).as("the second session's row is live").isTrue();

        assertProblem(send(HttpMethod.POST, TotpFactors.VERIFICATION, acting.cookie(), acting.token(),
                JSON.writeValueAsString(Map.of("code", factors.wrongCode(secret)))), ErrorCode.FACTOR_DISABLED);

        assertThat(jdbc.queryForMap("SELECT force_password_change, credential_issued_at FROM users WHERE id = ?",
                admin.id())).containsEntry("force_password_change", true).containsEntry("credential_issued_at", null);
        for (Signed session : new Signed[] {other, acting}) {
            assertThat(rows.exists(SessionRows.idOf(session.cookie()))).as("row gone").isFalse();
            assertProblem(send(HttpMethod.GET, "/api/profile", session.cookie(), null, null),
                    ErrorCode.AUTHENTICATION_FAILED);
        }
    }
}
