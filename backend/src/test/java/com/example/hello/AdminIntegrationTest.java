package com.example.hello;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdminIntegrationTest extends HttpIntegrationSupport {
  @Test
  void adminListsOnlySafeFieldsAndCannotModifySelf() throws Exception {
    Browser admin = new Browser();
    assertThat(login(admin, "operator", "Test-Operator-Password-2026").statusCode()).isEqualTo(200);
    String id = json.readTree(admin.get("/api/auth/me").body()).path("id").asText();
    var missingCsrf =
        admin.client.send(
            HttpRequest.newBuilder(uri("/api/admin/users/" + id + "/status"))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString("{\"enabled\":false}"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(missingCsrf.statusCode()).isEqualTo(403);
    HttpResponse<String> list = admin.get("/api/admin/users");
    assertThat(list.statusCode()).isEqualTo(200);
    assertThat(list.body()).doesNotContain("password", "securityVersion", "failedLogin");
    assertThat(
            admin
                .mutate("PATCH", "/api/admin/users/" + id + "/status", Map.of("enabled", false))
                .statusCode())
        .isEqualTo(400);
    assertThat(
            admin
                .mutate("PATCH", "/api/admin/users/" + id + "/role", Map.of("role", "USER"))
                .statusCode())
        .isEqualTo(400);
    assertThat(admin.mutate("DELETE", "/api/admin/users/" + id, Map.of()).statusCode())
        .isEqualTo(400);
  }

  @Test
  void userCannotAccessAnyAdminOperation() throws Exception {
    Browser user = new Browser();
    String username = register(user);
    assertThat(login(user, username, "A-strong-password-2026").statusCode()).isEqualTo(200);
    String id = json.readTree(user.get("/api/auth/me").body()).path("id").asText();
    assertThat(user.get("/api/admin/users").statusCode()).isEqualTo(403);
    assertThat(
            user.mutate("PATCH", "/api/admin/users/" + id + "/status", Map.of("enabled", false))
                .statusCode())
        .isEqualTo(403);
    assertThat(
            user.mutate("PATCH", "/api/admin/users/" + id + "/role", Map.of("role", "ADMIN"))
                .statusCode())
        .isEqualTo(403);
    assertThat(user.mutate("DELETE", "/api/admin/users/" + id, Map.of()).statusCode())
        .isEqualTo(403);
  }

  @Test
  void adminMutationsRevokeSessionsAndRejectInvalidRoles() throws Exception {
    Browser admin = new Browser();
    login(admin, "operator", "Test-Operator-Password-2026");
    Browser user = new Browser();
    String username = register(user);
    login(user, username, "A-strong-password-2026");
    String id = json.readTree(user.get("/api/auth/me").body()).path("id").asText();
    String path = "/api/admin/users/" + id;
    assertThat(admin.mutate("PATCH", path + "/role", Map.of("role", "SUPERUSER")).statusCode())
        .isEqualTo(400);
    assertThat(admin.mutate("PATCH", path + "/role", Map.of("role", "ADMIN")).statusCode())
        .isEqualTo(200);
    assertThat(user.get("/api/hello").statusCode()).isEqualTo(401);
    login(user, username, "A-strong-password-2026");
    assertThat(user.get("/api/admin/users").statusCode()).isEqualTo(200);
    assertThat(admin.mutate("PATCH", path + "/role", Map.of("role", "USER")).statusCode())
        .isEqualTo(200);
    assertThat(user.get("/api/admin/users").statusCode()).isEqualTo(401);
    login(user, username, "A-strong-password-2026");
    assertThat(admin.mutate("PATCH", path + "/status", Map.of("enabled", false)).statusCode())
        .isEqualTo(200);
    assertThat(user.get("/api/hello").statusCode()).isEqualTo(401);
    assertThat(login(new Browser(), username, "A-strong-password-2026").statusCode())
        .isEqualTo(401);
    assertThat(admin.mutate("PATCH", path + "/status", Map.of("enabled", true)).statusCode())
        .isEqualTo(200);
    assertThat(login(user, username, "A-strong-password-2026").statusCode()).isEqualTo(200);
    assertThat(admin.mutate("DELETE", path, Map.of()).statusCode()).isEqualTo(204);
    assertThat(user.get("/api/hello").statusCode()).isEqualTo(401);
    assertThat(
            database.queryForObject(
                "select count(*) from users where username = ?", Integer.class, username))
        .isZero();
  }
}
