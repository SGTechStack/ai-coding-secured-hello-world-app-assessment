package sg.securedhello.registration;

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

import sg.securedhello.credential.CredentialTokenType;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.CtxPortTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.Registrations;
import sg.securedhello.testsupport.SessionRows;
import sg.securedhello.testsupport.SignedIn;

import tools.jackson.databind.json.JsonMapper;

/**
 * Activation redemption ends no session (ADR-037's last row), shown with raw cookies against the real JDBC session
 * store (level P). The reason is inline in the test: the account being activated has no sessions yet, so there is
 * nothing of its own to end, and nobody else's session is its business.
 */
class ActivationSessionsPortTest extends CtxPortTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

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

    /** Posts {@code json} to {@code path} on a freshly bootstrapped anonymous session; returns the status. */
    private int anonymousPost(String path, String json) {
        EntityExchangeResult<String> bootstrap = send(HttpMethod.GET, "/api/csrf", null, null, null);
        return send(HttpMethod.POST, path, cookieValue(bootstrap),
                JSON.readTree(bootstrap.getResponseBody()).get("token").asString(), json).getStatus().value();
    }

    @Test
    @Proves("T-SES-019")
    void anActivationLeavesAnUnrelatedLiveSessionAlone() {
        Accounts.Account bystander = new Accounts(jdbc, passwordEncoder).user();
        EntityExchangeResult<String> bootstrap = send(HttpMethod.GET, "/api/csrf", null, null, null);
        EntityExchangeResult<String> login = send(HttpMethod.POST, "/api/login", cookieValue(bootstrap),
                JSON.readTree(bootstrap.getResponseBody()).get("token").asString(),
                SignedIn.credentials(bystander.username(), bystander.password()));
        assertThat(login.getStatus().value()).isEqualTo(200);
        String live = cookieValue(login);

        String username = Registrations.freshUsername();
        String email = Registrations.emailFor(username);
        assertThat(anonymousPost("/api/register", JSON.writeValueAsString(Map.of("username", username,
                "email", email)))).isEqualTo(202);
        String token = emails.latestToken(email, CredentialTokenType.ACTIVATION).orElseThrow();
        assertThat(anonymousPost("/api/register/activate", JSON.writeValueAsString(Map.of("token", token,
                "password", Registrations.PASSWORD)))).isEqualTo(204);

        // Nothing is ended: the activated account has no sessions yet (ADR-037), and the bystander's is not its own.
        assertThat(new SessionRows(jdbc).exists(SessionRows.idOf(live))).isTrue();
        assertThat(send(HttpMethod.GET, "/api/hello", live, null, null).getStatus().value()).isEqualTo(200);
    }
}
