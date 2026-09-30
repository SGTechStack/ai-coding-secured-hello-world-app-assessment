package sg.example.helloauth.support;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import com.jayway.jsonpath.JsonPath;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Plays the SPA over real HTTP against a started application, like the MockMvc {@link Browser}.
 * It carries the session cookie by hand: the cookie is {@code Secure}, and these requests are
 * plain HTTP. For tests that restart the application.
 */
public final class HttpBrowser {

    private final HttpClient http = HttpClient.newHttpClient();
    private final String apiUrl;
    private String sessionCookie;
    private String csrfHeaderName;
    private String csrfToken;

    public HttpBrowser(ConfigurableApplicationContext api) {
        apiUrl = "http://localhost:" + api.getEnvironment().getProperty("local.server.port")
                + api.getEnvironment().getProperty("app.api.base-path");
    }

    public void fetchCsrf() throws IOException, InterruptedException {
        String body = get("/csrf").body();
        csrfHeaderName = JsonPath.read(body, "$.headerName");
        csrfToken = JsonPath.read(body, "$.token");
    }

    public HttpResponse<String> register(TestAccount account) throws IOException, InterruptedException {
        return post("/register", "application/json", """
                {"username":"%s","email":"%s","password":"%s"}"""
                .formatted(account.username(), account.email(), account.password()));
    }

    public HttpResponse<String> login(String username, String password) throws IOException, InterruptedException {
        return post("/login", "application/x-www-form-urlencoded",
                "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                        + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8));
    }

    public HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return send(request(path).GET());
    }

    public HttpResponse<String> post(String path, String contentType, String body)
            throws IOException, InterruptedException {
        return send(request(path).header(csrfHeaderName, csrfToken).header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    public String sessionCookie() {
        return sessionCookie;
    }

    /** Carries a session cookie captured by another browser, e.g. before a restart. */
    public void useSessionCookie(String cookie) {
        sessionCookie = cookie;
    }

    private HttpRequest.Builder request(String path) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(apiUrl + path));
        return sessionCookie == null ? request : request.header("Cookie", sessionCookie);
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        response.headers().allValues("Set-Cookie").stream()
                .filter(cookie -> cookie.startsWith(Browser.SESSION_COOKIE + "="))
                .findFirst()
                .ifPresent(cookie -> sessionCookie = cookie.substring(0, cookie.indexOf(';')));
        return response;
    }
}
