package sg.securedhello.passwordreset;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import sg.securedhello.config.OriginsProperties;
import sg.securedhello.credential.CredentialTokenType;
import sg.securedhello.error.ErrorCode;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.CtxPortTest;
import sg.securedhello.testsupport.PasswordResets;
import sg.securedhello.testsupport.ProblemAssertions;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.Registrations;
import sg.securedhello.testsupport.SessionRows;
import sg.securedhello.testsupport.SignedIn;

import tools.jackson.databind.json.JsonMapper;

/**
 * Password reset over a real port (level P): redemption ends every session of the account, shown with raw cookies
 * against the real JDBC session store (ADR-035; ADR-037), and a forged {@code Host} or {@code X-Forwarded-Host},
 * written on the wire by hand, never reaches a link (REJ-022).
 */
class PasswordResetPortTest extends CtxPortTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ATTACKER = "attacker.example";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OriginsProperties origins;

    /** An anonymous session: its cookie value and CSRF token. */
    private record Anonymous(String cookie, String token) {
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
        return result.getResponseHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0].split("=", 2)[1];
    }

    private Anonymous anonymous() {
        EntityExchangeResult<String> bootstrap = send(HttpMethod.GET, "/api/csrf", null, null, null);
        return new Anonymous(cookieValue(bootstrap), JSON.readTree(bootstrap.getResponseBody()).get("token")
                .asString());
    }

    private String signIn(Account account) {
        Anonymous session = anonymous();
        EntityExchangeResult<String> login = send(HttpMethod.POST, "/api/login", session.cookie(), session.token(),
                SignedIn.credentials(account.username(), account.password()));
        assertThat(login.getStatus().value()).isEqualTo(200);
        return cookieValue(login);
    }

    @Test
    @Proves("T-SES-014")
    void redemptionEndsEverySessionOfTheAccountAndCreatesNone() {
        Account account = new Accounts(jdbc, passwordEncoder).user();
        SessionRows rows = new SessionRows(jdbc);
        String displaced = signIn(account);
        String live = signIn(account);
        Anonymous requester = anonymous();
        assertThat(send(HttpMethod.POST, "/api/password-reset/request", requester.cookie(), requester.token(),
                JSON.writeValueAsString(Map.of("email", PasswordResets.emailOf(account)))).getStatus().value())
                .isEqualTo(202);
        String token = emails.latestToken(PasswordResets.emailOf(account), CredentialTokenType.PASSWORD_RESET)
                .orElseThrow();

        Anonymous redeemer = anonymous();
        EntityExchangeResult<String> confirm = send(HttpMethod.POST, "/api/password-reset/confirm", redeemer.cookie(),
                redeemer.token(), JSON.writeValueAsString(Map.of("token", token, "password",
                        PasswordResets.NEW_PASSWORD)));

        assertThat(confirm.getStatus().value()).isEqualTo(204);
        assertThat(confirm.getResponseHeaders().get(HttpHeaders.SET_COOKIE)).as("redemption returns no session")
                .isNull();
        for (String cookie : new String[] {live, displaced}) {
            assertThat(rows.exists(SessionRows.idOf(cookie))).isFalse();
        }
        EntityExchangeResult<String> replay = send(HttpMethod.GET, "/api/hello", live, null, null);
        ProblemAssertions.assertProblem(replay.getStatus().value(),
                replay.getResponseHeaders().getFirst(HttpHeaders.CONTENT_TYPE), replay.getResponseBody(),
                replay.getResponseHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE), ErrorCode.AUTHENTICATION_FAILED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Host", "X-Forwarded-Host"})
    @Proves({"T-CRED-016", "T-CRED-017"})
    void aForgedHostHeaderNeverShapesAResetOrActivationLink(String forged) throws IOException {
        Account account = new Accounts(jdbc, passwordEncoder).user();
        String resetEmail = PasswordResets.emailOf(account);
        String username = Registrations.freshUsername();
        String activationEmail = Registrations.emailFor(username);

        assertThat(rawPost(forged, "/api/password-reset/request", JSON.writeValueAsString(Map.of("email",
                resetEmail)))).startsWith("HTTP/1.1 202");
        assertThat(rawPost(forged, "/api/register", JSON.writeValueAsString(Map.of("username", username, "email",
                activationEmail)))).startsWith("HTTP/1.1 202");

        assertThat(emails.to(resetEmail)).singleElement().satisfies(email -> assertThat(email.link().toString())
                .startsWith(origins.spa() + "/reset#token="));
        assertThat(emails.to(activationEmail)).singleElement().satisfies(email -> assertThat(email.link().toString())
                .startsWith(origins.spa() + "/activate#token="));
    }

    /**
     * POSTs {@code json} on a fresh anonymous session, written by hand so the {@code Host} header can be forged (HTTP
     * clients refuse to); {@code forged} is set to the attacker's host. Returns the raw response.
     */
    private String rawPost(String forged, String path, String json) throws IOException {
        Anonymous session = anonymous();
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        String host = forged.equals("Host") ? ATTACKER : "localhost:" + port;
        String extra = forged.equals("Host") ? "" : forged + ": " + ATTACKER + "\r\n";
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(10_000);
            OutputStream out = socket.getOutputStream();
            out.write(("POST " + path + " HTTP/1.1\r\n"
                    + "Host: " + host + "\r\n"
                    + extra
                    + "Content-Type: application/json\r\n"
                    + "Content-Length: " + body.length + "\r\n"
                    + "Cookie: SESSION=" + session.cookie() + "\r\n"
                    + "X-CSRF-TOKEN: " + session.token() + "\r\n"
                    + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(body);
            out.flush();
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
