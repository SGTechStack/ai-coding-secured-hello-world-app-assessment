package com.example.helloauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.helloauth.support.ApiClient;
import com.example.helloauth.support.ApiIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The cross-cutting requirements that belong to no single story: CORS with credentials, and CSRF on
 * every state-changing endpoint.
 *
 * <p>These are worth testing precisely because they are invisible when correct. A CORS allow-list that
 * quietly lets any origin through, or a CSRF filter that is configured but never actually consulted,
 * both look exactly like a working application until someone attacks it.
 */
class CrossOriginSecurityTest extends ApiIntegrationTest {

    private static final String FRONTEND_ORIGIN = "http://localhost:3000";

    @Test
    @DisplayName("a preflight from the configured origin is allowed, with credentials")
    void allowsTheConfiguredOrigin() {
        ApiClient.Response response =
                client.options(
                        "/api/auth/login",
                        Map.of(
                                "Origin", FRONTEND_ORIGIN,
                                "Access-Control-Request-Method", "POST",
                                "Access-Control-Request-Headers", "content-type,x-xsrf-token"));

        assertThat(response.status()).isIn(200, 204);
        assertThat(response.headers().first("access-control-allow-origin"))
                .contains(FRONTEND_ORIGIN);
        // Without this the browser discards the session cookie and the whole mechanism silently
        // does nothing. It also rules out a wildcard origin, which cannot coexist with it.
        assertThat(response.headers().first("access-control-allow-credentials")).contains("true");
    }

    @Test
    @DisplayName("a preflight from any other origin is refused")
    void rejectsUnknownOrigins() {
        ApiClient.Response response =
                client.options(
                        "/api/auth/login",
                        Map.of(
                                "Origin", "http://evil.example",
                                "Access-Control-Request-Method", "POST"));

        assertThat(response.headers().first("access-control-allow-origin")).isEmpty();
        assertThat(response.status()).isNotIn(200, 204);
    }

    @Test
    @DisplayName("login without a CSRF token is refused")
    void loginRequiresCsrfToken() {
        givenUser("alice");

        ApiClient.Response response =
                new ApiClient(port)
                        .post(
                                "/api/auth/login",
                                Map.of("username", "alice", "password", PASSWORD));

        assertThat(response.status()).isEqualTo(403);
        assertThat(response.json().get("code").asText()).isEqualTo("invalid_csrf_token");
    }

    @Test
    @DisplayName("registration without a CSRF token is refused")
    void registrationRequiresCsrfToken() {
        ApiClient.Response response =
                new ApiClient(port)
                        .post(
                                "/api/auth/register",
                                Map.of(
                                        "username", "alice",
                                        "email", "alice@example.com",
                                        "password", PASSWORD));

        assertThat(response.status()).isEqualTo(403);
        assertThat(accounts.count()).isZero();
    }

    @Test
    @DisplayName("both password reset endpoints require a CSRF token")
    void passwordResetRequiresCsrfToken() {
        givenUser("alice");
        ApiClient noToken = new ApiClient(port);

        assertThat(
                        noToken.post(
                                        "/api/auth/password-reset/request",
                                        Map.of("email", "alice@example.com"))
                                .status())
                .isEqualTo(403);
        assertThat(
                        noToken.post(
                                        "/api/auth/password-reset/confirm",
                                        Map.of("token", "whatever", "password", NEW_PASSWORD))
                                .status())
                .isEqualTo(403);
        assertThat(emails.sent()).isEmpty();
    }

    @Test
    @DisplayName("reads do not need a CSRF token")
    void readsAreNotCsrfProtected() {
        givenUser("alice");
        String cookie = loggedInAs("alice").sessionCookie();

        ApiClient.Response response =
                new ApiClient(port).withSessionCookie(cookie).get("/api/hello");

        assertThat(response.status()).isEqualTo(200);
    }

    @Test
    @DisplayName("the CSRF bootstrap endpoint tells the client which header to use")
    void csrfEndpointDescribesItsOwnContract() {
        ApiClient.Response response = client.get("/api/auth/csrf");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().get("headerName").asText()).isEqualTo("X-XSRF-TOKEN");
        assertThat(response.json().get("token").asText()).isNotBlank();
    }
}
