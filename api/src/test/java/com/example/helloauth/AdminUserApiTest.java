package com.example.helloauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.helloauth.domain.Account;
import com.example.helloauth.domain.Role;
import com.example.helloauth.support.ApiClient;
import com.example.helloauth.support.ApiIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Stories 8 to 11 — the admin module, its role gate, and the self-action guards. */
class AdminUserApiTest extends ApiIntegrationTest {

    @Test
    @DisplayName("an admin sees every account, and never a password hash")
    void listsAccounts() {
        givenAdmin("root");
        givenUser("alice");

        ApiClient.Response response = loggedInAs("root").get("/api/admin/users");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json()).hasSize(2);
        assertThat(response.json().get(0).get("username").asText()).isEqualTo("root");
        assertThat(response.json().get(0).get("email").asText()).isEqualTo("root@example.com");
        assertThat(response.json().get(0).get("role").asText()).isEqualTo("ADMIN");
        assertThat(response.json().get(0).has("enabled")).isTrue();
        assertThat(response.json().get(0).has("createdAt")).isTrue();

        // Checked against the raw body rather than field by field, so a hash cannot arrive under a
        // name this test did not think to look for.
        assertThat(response.body())
                .doesNotContain("passwordHash")
                .doesNotContain("password")
                .doesNotContain("$2a$")
                .doesNotContain("$2b$");
    }

    /** The role-enforcement test the PRD names, applied to the whole path rather than one endpoint. */
    @Test
    @DisplayName("a USER gets 403 from every admin endpoint")
    void nonAdminsAreForbidden() {
        givenAdmin("root");
        givenUser("alice");
        Account target = accounts.findByUsername("root").orElseThrow();
        ApiClient alice = loggedInAs("alice");

        assertThat(alice.get("/api/admin/users").status()).isEqualTo(403);
        assertThat(
                        alice.patch(
                                        "/api/admin/users/" + target.getId() + "/status",
                                        Map.of("enabled", false))
                                .status())
                .isEqualTo(403);
        assertThat(
                        alice.patch(
                                        "/api/admin/users/" + target.getId() + "/role",
                                        Map.of("role", "USER"))
                                .status())
                .isEqualTo(403);
        assertThat(alice.delete("/api/admin/users/" + target.getId()).status()).isEqualTo(403);

        assertThat(accounts.findByUsername("root").orElseThrow().getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("a visitor gets 401 from the admin endpoints, not 403")
    void anonymousCallersAreUnauthenticated() {
        assertThat(client.get("/api/admin/users").status()).isEqualTo(401);
    }

    @Test
    @DisplayName("disabling an account stops future logins and ends its current sessions")
    void disablingAnAccount() {
        givenAdmin("root");
        Account alice = givenUser("alice");
        ApiClient aliceSession = loggedInAs("alice");
        assertThat(aliceSession.get("/api/hello").status()).isEqualTo(200);

        ApiClient.Response response =
                loggedInAs("root")
                        .patch(
                                "/api/admin/users/" + alice.getId() + "/status",
                                Map.of("enabled", false));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().get("enabled").asBoolean()).isFalse();
        assertThat(accounts.findById(alice.getId()).orElseThrow().isEnabled()).isFalse();
        assertThat(login(newClient(), "alice", PASSWORD).status()).isEqualTo(401);
        // Beyond the PRD, and the reason it matters: authorities are cached in the session, so
        // without ending it a disabled account would keep working until the session timed out.
        assertThat(aliceSession.get("/api/hello").status()).isEqualTo(401);
    }

    @Test
    @DisplayName("re-enabling an account restores login")
    void reEnablingAnAccount() {
        givenAdmin("root");
        Account alice = givenUser("alice");
        ApiClient root = loggedInAs("root");

        root.patch("/api/admin/users/" + alice.getId() + "/status", Map.of("enabled", false));
        root.patch("/api/admin/users/" + alice.getId() + "/status", Map.of("enabled", true));

        assertThat(login(newClient(), "alice", PASSWORD).status()).isEqualTo(200);
    }

    @Test
    @DisplayName("an admin cannot disable themselves")
    void cannotDisableSelf() {
        Account root = givenAdmin("root");

        ApiClient.Response response =
                loggedInAs("root")
                        .patch(
                                "/api/admin/users/" + root.getId() + "/status",
                                Map.of("enabled", false));

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.json().get("code").asText()).isEqualTo("self_action_forbidden");
        assertThat(accounts.findById(root.getId()).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    @DisplayName("an admin can promote a user, and the new role takes effect on the next login")
    void changingARole() {
        givenAdmin("root");
        Account alice = givenUser("alice");

        ApiClient.Response response =
                loggedInAs("root")
                        .patch("/api/admin/users/" + alice.getId() + "/role", Map.of("role", "ADMIN"));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().get("role").asText()).isEqualTo("ADMIN");
        assertThat(accounts.findById(alice.getId()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
        assertThat(loggedInAs("alice").get("/api/admin/users").status()).isEqualTo(200);
    }

    @Test
    @DisplayName("demoting an admin ends their session, so the old authority cannot outlive it")
    void demotionEndsTheSession() {
        givenAdmin("root");
        Account other = givenAdmin("second");
        ApiClient otherSession = loggedInAs("second");
        assertThat(otherSession.get("/api/admin/users").status()).isEqualTo(200);

        loggedInAs("root")
                .patch("/api/admin/users/" + other.getId() + "/role", Map.of("role", "USER"));

        assertThat(otherSession.get("/api/admin/users").status()).isEqualTo(401);
    }

    @Test
    @DisplayName("an admin cannot demote themselves")
    void cannotDemoteSelf() {
        Account root = givenAdmin("root");

        ApiClient.Response response =
                loggedInAs("root")
                        .patch("/api/admin/users/" + root.getId() + "/role", Map.of("role", "USER"));

        assertThat(response.status()).isEqualTo(409);
        assertThat(accounts.findById(root.getId()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("an admin can delete another account, tokens and all")
    void deletingAnAccount() {
        givenAdmin("root");
        Account alice = givenUser("alice");
        client.post("/api/auth/password-reset/request", Map.of("email", "alice@example.com"));
        assertThat(resetTokens.count()).isEqualTo(1);

        ApiClient.Response response =
                loggedInAs("root").delete("/api/admin/users/" + alice.getId());

        assertThat(response.status()).isEqualTo(204);
        assertThat(accounts.findById(alice.getId())).isEmpty();
        // The tokens reference the account, so leaving them would either orphan rows or break the
        // foreign key. Either way it is the delete that has to deal with it.
        assertThat(resetTokens.count()).isZero();
    }

    @Test
    @DisplayName("an admin cannot delete themselves")
    void cannotDeleteSelf() {
        Account root = givenAdmin("root");

        ApiClient.Response response = loggedInAs("root").delete("/api/admin/users/" + root.getId());

        assertThat(response.status()).isEqualTo(409);
        assertThat(accounts.findById(root.getId())).isPresent();
    }

    @Test
    @DisplayName("an unknown account id is a 404")
    void unknownAccountIsNotFound() {
        givenAdmin("root");

        ApiClient.Response response =
                loggedInAs("root")
                        .delete("/api/admin/users/00000000-0000-0000-0000-000000000000");

        assertThat(response.status()).isEqualTo(404);
    }

    /**
     * All three mutations, not just the delete. The CSRF filter is configured globally so one passing
     * case is suggestive, but the PRD names "admin mutations" as a set and a per-endpoint assertion is
     * what would actually catch someone adding a path exemption later.
     */
    @Test
    @DisplayName("every admin mutation requires a CSRF token")
    void adminMutationsAreCsrfProtected() {
        givenAdmin("root");
        Account alice = givenUser("alice");
        String cookie = loggedInAs("root").sessionCookie();
        // A client holding a valid session but no CSRF token: exactly what a cross-site form post is.
        ApiClient noToken = new ApiClient(port).withSessionCookie(cookie);

        assertThat(
                        noToken.patch(
                                        "/api/admin/users/" + alice.getId() + "/status",
                                        Map.of("enabled", false))
                                .status())
                .isEqualTo(403);
        assertThat(
                        noToken.patch(
                                        "/api/admin/users/" + alice.getId() + "/role",
                                        Map.of("role", "ADMIN"))
                                .status())
                .isEqualTo(403);
        assertThat(noToken.delete("/api/admin/users/" + alice.getId()).status()).isEqualTo(403);

        Account untouched = accounts.findById(alice.getId()).orElseThrow();
        assertThat(untouched.isEnabled()).isTrue();
        assertThat(untouched.getRole()).isEqualTo(Role.USER);
    }
}
