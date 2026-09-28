package com.example.helloauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.helloauth.support.ApiClient;
import com.example.helloauth.support.ApiIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Story 4 — logout ends the session for real, not just in the client's memory. */
class LogoutApiTest extends ApiIntegrationTest {

    @Test
    @DisplayName("logout invalidates the server-side session and clears the cookie")
    void logoutEndsTheSession() {
        givenUser("alice");
        ApiClient session = loggedInAs("alice");
        assertThat(session.get("/api/hello").status()).isEqualTo(200);
        assertThat(sessionRowCount()).isEqualTo(1);

        ApiClient.Response response = session.post("/api/auth/logout", null);

        assertThat(response.status()).isEqualTo(204);
        assertThat(session.sessionCookie()).as("the cookie was cleared").isNull();
        assertThat(sessionRowCount()).as("the server-side session is gone").isZero();
        assertThat(session.get("/api/hello").status()).isEqualTo(401);
    }

    /**
     * The requirement that actually matters, and the one a client-side-only logout would fail: a
     * cookie captured while the session was alive must be worthless afterwards. Forgetting the cookie
     * is not ending a session — anyone who copied it would still be logged in.
     */
    @Test
    @DisplayName("a session cookie captured before logout is rejected when replayed after it")
    void replayedCookieIsRejected() {
        givenUser("alice");
        ApiClient session = loggedInAs("alice");
        String capturedCookie = session.sessionCookie();
        assertThat(capturedCookie).isNotBlank();

        session.post("/api/auth/logout", null);

        // A brand-new client, as an attacker would have: nothing but the stolen cookie.
        ApiClient replay = newClient().withSessionCookie(capturedCookie);

        assertThat(replay.get("/api/hello").status()).isEqualTo(401);
        assertThat(replay.get("/api/auth/me").json().get("authenticated").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("logout requires a CSRF token like any other state-changing call")
    void logoutIsCsrfProtected() {
        givenUser("alice");
        ApiClient session = loggedInAs("alice");
        String cookie = session.sessionCookie();

        // No primeCsrf(), so no token travels with the request.
        ApiClient.Response response =
                new ApiClient(port).withSessionCookie(cookie).post("/api/auth/logout", null);

        assertThat(response.status()).isEqualTo(403);
        assertThat(session.get("/api/hello").status())
                .as("the session survived the rejected logout")
                .isEqualTo(200);
    }

    private int sessionRowCount() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION", Integer.class);
        return count == null ? 0 : count;
    }
}
