package com.example.hello;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AuthIntegrationTest extends HttpIntegrationSupport {
  @Autowired com.example.hello.config.AdminBootstrap bootstrap;

  @Test
  void bootstrapIsIdempotentAndExpiredSessionsAreRejected() throws Exception {
    long before =
        database.queryForObject("select count(*) from users where role = 'ADMIN'", Long.class);
    bootstrap.run(new org.springframework.boot.DefaultApplicationArguments());
    assertThat(
            database.queryForObject("select count(*) from users where role = 'ADMIN'", Long.class))
        .isEqualTo(before);
    Browser browser = new Browser();
    String username = register(browser);
    login(browser, username, "A-strong-password-2026");
    database.update(
        "update SPRING_SESSION set LAST_ACCESS_TIME = 0, EXPIRY_TIME = 0 where PRINCIPAL_NAME = ?",
        username);
    assertThat(browser.get("/api/hello").statusCode()).isEqualTo(401);
  }

  @Test
  void registrationValidatesPolicyUniquenessAndStoresOnlyBcrypt() throws Exception {
    Browser browser = new Browser();
    String username = register(browser);
    Map<String, Object> row =
        database.queryForMap("select * from users where username = ?", username);
    assertThat(row.get("ROLE")).isEqualTo("USER");
    assertThat(row.get("ENABLED")).isEqualTo(true);
    assertThat(passwords.matches("A-strong-password-2026", (String) row.get("PASSWORD_HASH")))
        .isTrue();
    assertThat(
            browser
                .mutate(
                    "POST",
                    "/api/auth/register",
                    Map.of(
                        "username",
                        username,
                        "email",
                        "other@example.com",
                        "password",
                        "A-strong-password-2026"))
                .statusCode())
        .isEqualTo(409);
    assertThat(
            browser
                .mutate(
                    "POST",
                    "/api/auth/register",
                    Map.of(
                        "username",
                        "other" + username,
                        "email",
                        username + "@example.com",
                        "password",
                        "A-strong-password-2026"))
                .statusCode())
        .isEqualTo(409);
    for (String password : new String[] {"short", "é".repeat(37)}) {
      assertThat(
              browser
                  .mutate(
                      "POST",
                      "/api/auth/register",
                      Map.of(
                          "username",
                          "bad" + username,
                          "email",
                          "bad" + username + "@example.com",
                          "password",
                          password))
                  .statusCode())
          .isEqualTo(400);
    }
  }

  @Test
  void wrongUnknownDisabledAndLockedCredentialsHaveIdenticalErrors() throws Exception {
    Browser browser = new Browser();
    String username = register(browser);
    HttpResponse<String> wrong = login(browser, username, "wrong-password");
    HttpResponse<String> unknown = login(new Browser(), "missinguser", "wrong-password");
    HttpResponse<String> supplementary = login(new Browser(), "😀unknown", "wrong-password");
    database.update("update users set enabled = false where username = ?", username);
    HttpResponse<String> disabled = login(new Browser(), username, "A-strong-password-2026");
    database.update(
        "update users set enabled = true, locked_until = DATEADD('MINUTE', 15, CURRENT_TIMESTAMP) where username = ?",
        username);
    HttpResponse<String> locked = login(new Browser(), username, "A-strong-password-2026");
    for (HttpResponse<String> response :
        java.util.List.of(wrong, unknown, supplementary, disabled, locked)) {
      assertThat(response.statusCode()).isEqualTo(401);
      assertThat(response.body()).isEqualTo(wrong.body());
    }
  }

  @Test
  void distributedFailuresLockAccountAndCooldownResetsCounter() throws Exception {
    String username = register(new Browser());
    for (int failure = 0; failure < 5; failure++) {
      assertThat(login(new Browser(), username, "wrong-password").statusCode()).isEqualTo(401);
    }
    assertThat(
            database.queryForObject(
                "select failed_login_attempts from users where username = ?",
                Integer.class,
                username))
        .isEqualTo(5);
    assertThat(login(new Browser(), username, "A-strong-password-2026").statusCode())
        .isEqualTo(401);
    database.update(
        "update users set locked_until = DATEADD('MINUTE', -1, CURRENT_TIMESTAMP) where username = ?",
        username);
    assertThat(login(new Browser(), username, "A-strong-password-2026").statusCode())
        .isEqualTo(200);
    assertThat(
            database.queryForObject(
                "select failed_login_attempts from users where username = ?",
                Integer.class,
                username))
        .isZero();
  }

  @Test
  void ipThrottleIsIndependentAndStopsOneSourceLockingAnAccount() throws Exception {
    Browser attacker = new Browser();
    String username = register(new Browser());
    for (int failure = 0; failure < 3; failure++) {
      assertThat(
              login(attacker, failure == 0 ? "unknown" : username, "wrong-password").statusCode())
          .isEqualTo(401);
    }
    HttpResponse<String> throttled = login(attacker, username, "A-strong-password-2026");
    assertThat(throttled.statusCode()).isEqualTo(429);
    assertThat(throttled.headers().firstValue("Retry-After")).isPresent();
    assertThat(
            database.queryForObject(
                "select failed_login_attempts from users where username = ?",
                Integer.class,
                username))
        .isEqualTo(2);
    assertThat(login(new Browser(), username, "A-strong-password-2026").statusCode())
        .isEqualTo(200);
  }

  @Test
  void csrfIsRequiredForEveryMutationAndCorsIsAnExactAllowList() throws Exception {
    for (String path :
        new String[] {
          "register", "login", "logout", "password-reset/request", "password-reset/confirm"
        }) {
      HttpResponse<String> response =
          HttpClient.newHttpClient()
              .send(
                  HttpRequest.newBuilder(uri("/api/auth/" + path))
                      .header("Content-Type", "application/json")
                      .POST(HttpRequest.BodyPublishers.ofString("{}"))
                      .build(),
                  HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).as(path).isEqualTo(403);
    }
    for (String origin : new String[] {"http://localhost:3000", "https://evil.example"}) {
      HttpResponse<String> response =
          HttpClient.newHttpClient()
              .send(
                  HttpRequest.newBuilder(uri("/api/auth/login"))
                      .header("Origin", origin)
                      .header("Access-Control-Request-Method", "POST")
                      .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                      .build(),
                  HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(origin.contains("localhost") ? 200 : 403);
      if (response.statusCode() == 200) {
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).contains(origin);
        assertThat(response.headers().firstValue("Access-Control-Allow-Credentials"))
            .contains("true");
      }
    }
  }

  @Test
  void loginRotatesSessionAndBootstrapAdminIsAvailable() throws Exception {
    Browser browser = new Browser();
    HttpResponse<String> csrf = browser.get("/api/auth/csrf");
    String cookie = csrf.headers().firstValue("Set-Cookie").orElseThrow();
    assertThat(cookie).contains("HttpOnly", "SameSite=Lax");
    String previous = browser.cookies.getCookieStore().getCookies().getFirst().getValue();
    assertThat(login(browser, "operator", "Test-Operator-Password-2026").statusCode())
        .isEqualTo(200);
    assertThat(browser.cookies.getCookieStore().getCookies().getFirst().getValue())
        .isNotEqualTo(previous);
    assertThat(json.readTree(browser.get("/api/auth/me").body()).path("role").asText())
        .isEqualTo("ADMIN");
  }

  @Test
  void registersLogsInAndRejectsReplayedSessionAfterLogout() throws Exception {
    Browser browser = new Browser();
    String username = "user" + UUID.randomUUID().toString().substring(0, 8);
    assertThat(
            browser
                .mutate(
                    "POST",
                    "/api/auth/register",
                    Map.of(
                        "username",
                        username,
                        "email",
                        username + "@example.com",
                        "password",
                        "A-strong-password-2026"))
                .statusCode())
        .isEqualTo(201);
    assertThat(browser.get("/api/hello").statusCode()).isEqualTo(401);
    assertThat(
            browser
                .mutate(
                    "POST",
                    "/api/auth/login",
                    Map.of("username", username, "password", "A-strong-password-2026"))
                .statusCode())
        .isEqualTo(200);
    assertThat(json.readTree(browser.get("/api/hello").body()).asText())
        .isEqualTo("Hello, " + username);
    String capturedCookie =
        browser.cookies.getCookieStore().getCookies().stream()
            .filter(cookie -> cookie.getName().equals("SESSION"))
            .map(cookie -> cookie.getName() + "=" + cookie.getValue())
            .findFirst()
            .orElseThrow();
    assertThat(browser.mutate("POST", "/api/auth/logout", Map.of()).statusCode()).isEqualTo(204);
    HttpResponse<String> replay =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(uri("/api/hello"))
                    .header("Cookie", capturedCookie)
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString());
    assertThat(replay.statusCode()).isEqualTo(401);
  }
}
