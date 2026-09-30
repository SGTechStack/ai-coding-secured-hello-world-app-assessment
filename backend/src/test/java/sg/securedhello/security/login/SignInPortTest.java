package sg.securedhello.security.login;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
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
import sg.securedhello.testsupport.CtxPortTest;
import sg.securedhello.testsupport.ProblemAssertions;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SessionCookies;
import sg.securedhello.testsupport.SessionRows;
import sg.securedhello.testsupport.SignedIn;

import tools.jackson.databind.json.JsonMapper;

/** Sign-in and sign-out as Tomcat really answers them: the raw {@code Set-Cookie} headers (level P). */
class SignInPortTest extends CtxPortTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final List<String> setCookies = new ArrayList<>();

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
        EntityExchangeResult<String> result = request.exchange().expectBody(String.class).returnResult();
        setCookies.addAll(result.getResponseHeaders().getOrEmpty(HttpHeaders.SET_COOKIE));
        return result;
    }

    private static String cookieValue(EntityExchangeResult<?> result) {
        return SessionCookies.value(result.getResponseHeaders());
    }

    private static String token(EntityExchangeResult<String> result) {
        return JSON.readTree(result.getResponseBody()).get("token").asString();
    }

    private static void assertProblem(EntityExchangeResult<String> result, ErrorCode code) {
        ProblemAssertions.assertProblem(result.getStatus().value(),
                result.getResponseHeaders().getFirst(HttpHeaders.CONTENT_TYPE), result.getResponseBody(),
                result.getResponseHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE), code);
    }

    @Test
    @Proves({"T-SES-007", "T-CSRF-001"})
    void logoutEndsTheSessionClearsTheCookieAndNoCsrfCookieIsEverSet() {
        Accounts.Account alice = new Accounts(jdbc, passwordEncoder).user();

        EntityExchangeResult<String> bootstrap = send(HttpMethod.GET, "/api/csrf", null, null, null);
        String anonymous = cookieValue(bootstrap);
        EntityExchangeResult<String> login = send(HttpMethod.POST, "/api/login", anonymous, token(bootstrap),
                SignedIn.credentials(alice.username(), alice.password()));
        assertThat(login.getStatus().value()).isEqualTo(200);
        String signedIn = cookieValue(login);
        String token = token(send(HttpMethod.GET, "/api/csrf", signedIn, null, null));

        // An authenticated mutation the matrix refuses, and an anonymous error response.
        assertProblem(send(HttpMethod.PUT, "/api/profile", signedIn, token, "{}"), ErrorCode.ACCESS_DENIED);
        assertProblem(send(HttpMethod.GET, "/api/hello", null, null, null), ErrorCode.AUTHENTICATION_FAILED);

        EntityExchangeResult<String> logout = send(HttpMethod.POST, "/api/logout", signedIn, token, null);

        assertThat(logout.getStatus().value()).isEqualTo(204);
        assertThat(logout.getResponseHeaders().getFirst("Clear-Site-Data"))
                .isEqualTo("\"cache\", \"cookies\", \"storage\"");
        String expiry = logout.getResponseHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(expiry).startsWith("SESSION=;").contains("Max-Age=0", "Path=/", "HttpOnly", "SameSite=Strict");
        assertThat(new SessionRows(jdbc).exists(SessionRows.idOf(signedIn))).isFalse();
        assertProblem(send(HttpMethod.GET, "/api/hello", signedIn, null, null), ErrorCode.AUTHENTICATION_FAILED);

        // Besides the session cookie, only the device cookie a successful sign-in earns (ADR-075), and only once.
        assertThat(setCookies).as("the device cookie").filteredOn(SessionCookies::isDevice).hasSize(1);
        assertThat(setCookies).as("every other Set-Cookie of bootstrap, login, mutation, errors and logout")
                .filteredOn(header -> !SessionCookies.isDevice(header))
                .isNotEmpty().allSatisfy(header -> assertThat(header).startsWith("SESSION="));
    }
}
