package sg.securedhello.testsupport;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import tools.jackson.databind.json.JsonMapper;

/**
 * A browser-like session against a real port, for {@code restart} tests that drive a booted application over HTTP: the
 * {@code SESSION} cookie and the CSRF token bound to it (ADR-036). Immutable; each step returns the session it leaves.
 *
 * @param cookie the {@code name=value} pair of the session cookie, or {@code null} before the first bootstrap
 * @param token  the CSRF token for that session
 */
public record PortSession(RestTestClient client, String cookie, String token) {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** A client for {@code context}'s port, with no session yet. */
    public static RestTestClient client(ConfigurableApplicationContext context) {
        return RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + context.getEnvironment().getProperty("local.server.port")).build();
    }

    /** Fetches a token, which creates the anonymous session it is bound to. */
    public static PortSession bootstrap(RestTestClient client) {
        EntityExchangeResult<String> result = client.get().uri("/api/csrf").exchange().expectBody(String.class)
                .returnResult();
        String cookie = result.getResponseHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];
        return new PortSession(client, cookie, JSON.readTree(result.getResponseBody()).get("token").asString());
    }

    /** Signs in on this session and returns the rotated session with its new token; fails unless sign-in succeeds. */
    public PortSession signIn(String username, String password) {
        EntityExchangeResult<String> login = send(HttpMethod.POST, "/api/login", SignedIn.credentials(username,
                password));
        if (login.getStatus().value() != 200) {
            throw new AssertionError("sign-in answered " + login.getStatus() + ": " + login.getResponseBody());
        }
        String rotated = SessionCookies.pair(login.getResponseHeaders());
        return new PortSession(client, rotated, null).refreshed();
    }

    /** This session with the token {@code GET /api/csrf} now returns for it. */
    public PortSession refreshed() {
        EntityExchangeResult<String> result = get("/api/csrf");
        return new PortSession(client, cookie, JSON.readTree(result.getResponseBody()).get("token").asString());
    }

    /** {@code GET path} with this session's cookie. */
    public EntityExchangeResult<String> get(String path) {
        return client.get().uri(path).header(HttpHeaders.COOKIE, cookie).exchange().expectBody(String.class)
                .returnResult();
    }

    /** An unsafe request with this session's cookie, its token and a JSON body. */
    public EntityExchangeResult<String> send(HttpMethod method, String path, String json) {
        return client.method(method).uri(path).header(HttpHeaders.COOKIE, cookie).header(CsrfSession.HEADER, token)
                .contentType(MediaType.APPLICATION_JSON).body(json).exchange().expectBody(String.class)
                .returnResult();
    }

    /** This session's cookie and token, for the same application on another client (a later boot's port). */
    public PortSession on(RestTestClient other) {
        return new PortSession(other, cookie, token);
    }
}
