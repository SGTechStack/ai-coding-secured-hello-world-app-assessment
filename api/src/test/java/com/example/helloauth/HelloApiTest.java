package com.example.helloauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.helloauth.domain.Role;
import com.example.helloauth.support.ApiClient;
import com.example.helloauth.support.ApiIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Story 5 — the protected greeting. */
class HelloApiTest extends ApiIntegrationTest {

    @Test
    @DisplayName("an authenticated request gets exactly \"Hello, <username>\" as plain text")
    void greetsTheAuthenticatedUser() {
        givenUser("alice");
        ApiClient session = loggedInAs("alice");

        ApiClient.Response response = session.get("/api/hello");

        assertThat(response.status()).isEqualTo(200);
        // The exact bytes. No JSON envelope, no quotes, no trailing newline — the PRD writes the
        // response as a bare string, so anything else is a different response.
        assertThat(response.body()).isEqualTo("Hello, alice");
        assertThat(response.contentType()).startsWith("text/plain");
    }

    @Test
    @DisplayName("no session means 401, not a redirect to a login page")
    void refusesAnonymousRequests() {
        ApiClient.Response response = client.get("/api/hello");

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.json().get("code").asText()).isEqualTo("unauthenticated");
    }

    @Test
    @DisplayName("an invalid session cookie is treated as no session at all")
    void refusesAnUnknownSessionCookie() {
        ApiClient.Response response =
                newClient().withSessionCookie("not-a-real-session-id").get("/api/hello");

        assertThat(response.status()).isEqualTo(401);
    }

    @Test
    @DisplayName("an admin sees their own name, not a role")
    void greetsAdminsByName() {
        givenAdmin("root");

        assertThat(loggedInAs("root").get("/api/hello").body()).isEqualTo("Hello, root");
    }

    @Test
    @DisplayName("the session endpoint reports the role, so the frontend can route on it")
    void reportsTheCurrentSession() {
        givenAdmin("root");

        ApiClient.Response response = loggedInAs("root").get("/api/auth/me");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().get("authenticated").asBoolean()).isTrue();
        assertThat(response.json().get("username").asText()).isEqualTo("root");
        assertThat(response.json().get("role").asText()).isEqualTo(Role.ADMIN.name());
    }
}
