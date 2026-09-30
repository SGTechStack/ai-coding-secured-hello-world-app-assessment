package sg.example.helloauth.support;

import java.nio.charset.StandardCharsets;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Plays the part of the SPA in a browser: carries the session cookie between requests and
 * attaches the CSRF token it last fetched from {@code GET /csrf}, the way the real client does.
 */
public final class Browser {

    public static final String SESSION_COOKIE = "SESSION";

    private final MockMvcTester mvc;
    private final String basePath;
    private final String remoteAddress;
    private Cookie session;
    private String csrfToken;

    /** @param remoteAddress the address requests come from, or null for MockMvc's 127.0.0.1 */
    Browser(MockMvcTester mvc, String basePath, String remoteAddress) {
        this.mvc = mvc;
        this.basePath = basePath;
        this.remoteAddress = remoteAddress;
    }

    public MvcTestResult fetchCsrf() {
        MvcTestResult result = get("/csrf");
        csrfToken = JsonPath.read(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8),
                "$.token");
        return result;
    }

    public MvcTestResult register(String username, String email, String password) {
        return postJson("/register", """
                {"username":"%s","email":"%s","password":"%s"}""".formatted(username, email, password));
    }

    public MvcTestResult register(TestAccount account) {
        return register(account.username(), account.email(), account.password());
    }

    public MvcTestResult login(String username, String password) {
        return send(withCsrf(mvc.post().uri(basePath + "/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .formField("username", username)
                .formField("password", password)));
    }

    public MvcTestResult login(TestAccount account) {
        return login(account.username(), account.password());
    }

    public MvcTestResult logout() {
        return send(withCsrf(mvc.post().uri(basePath + "/logout")));
    }

    public MvcTestResult requestPasswordReset(String email) {
        return postJson("/password-reset/request", """
                {"email":"%s"}""".formatted(email));
    }

    public MvcTestResult confirmPasswordReset(String token, String newPassword) {
        return postJson("/password-reset/confirm", """
                {"token":"%s","newPassword":"%s"}""".formatted(token, newPassword));
    }

    public MvcTestResult listAccounts() {
        return get("/admin/users");
    }

    /** @param accountId the Account's id, as a {@code UUID} or its string form */
    public MvcTestResult setEnabled(Object accountId, boolean enabled) {
        return patchJson("/admin/users/" + accountId + "/status", "{\"enabled\":" + enabled + "}");
    }

    public MvcTestResult unlock(Object accountId) {
        return send(withCsrf(mvc.post().uri(basePath + "/admin/users/" + accountId + "/unlock")));
    }

    public MvcTestResult changeRole(Object accountId, String role) {
        return patchJson("/admin/users/" + accountId + "/role", "{\"role\":\"" + role + "\"}");
    }

    public MvcTestResult delete(Object accountId) {
        return send(withCsrf(mvc.delete().uri(basePath + "/admin/users/" + accountId)));
    }

    public MvcTestResult postJson(String path, String body) {
        return send(withCsrf(mvc.post().uri(basePath + path).contentType(MediaType.APPLICATION_JSON).content(body)));
    }

    public MvcTestResult patchJson(String path, String body) {
        return send(withCsrf(mvc.patch().uri(basePath + path).contentType(MediaType.APPLICATION_JSON).content(body)));
    }

    /** Fetches a CSRF token and registers, leaving this browser a Visitor with that Account. */
    public Browser registered(TestAccount account) {
        fetchCsrf();
        register(account).assertThat().hasStatus(201);
        return this;
    }

    /** Registers, logs in and fetches a fresh CSRF token, leaving this browser logged in. */
    public Browser registerAndLogin(TestAccount account) {
        registered(account);
        login(account).assertThat().hasStatusOk();
        fetchCsrf();
        return this;
    }

    public MvcTestResult get(String path) {
        return send(mvc.get().uri(basePath + path));
    }

    /** Sends a request built by the test, adding only this browser's session cookie and address. */
    public MvcTestResult send(MockMvcRequestBuilder request) {
        if (session != null) {
            request.cookie(session);
        }
        if (remoteAddress != null) {
            request.with(sent -> {
                sent.setRemoteAddr(remoteAddress);
                return sent;
            });
        }
        MvcTestResult result = request.exchange();
        Cookie issued = result.getResponse().getCookie(SESSION_COOKIE);
        if (issued != null) {
            session = issued.getMaxAge() == 0 ? null : issued;
        }
        return result;
    }

    public MockMvcRequestBuilder withCsrf(MockMvcRequestBuilder request) {
        return csrfToken == null ? request : request.header("X-CSRF-TOKEN", csrfToken);
    }

    public Cookie sessionCookie() {
        return session;
    }

    /** The CSRF token this browser last fetched. */
    public String csrfToken() {
        return csrfToken;
    }
}
