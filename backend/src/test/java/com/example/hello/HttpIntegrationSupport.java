package com.example.hello;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:auth;DB_CLOSE_DELAY=-1",
      "app.admin.username=operator", "app.admin.email=operator@example.com",
      "app.admin.password=Test-Operator-Password-2026", "app.cookie-secure=false"
    })
abstract class HttpIntegrationSupport {
  private static final AtomicInteger ADDRESS = new AtomicInteger(2);
  @LocalServerPort int port;
  @Autowired JdbcTemplate database;
  @Autowired PasswordEncoder passwords;
  final ObjectMapper json = new ObjectMapper();

  String register(Browser browser) throws Exception {
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
    return username;
  }

  HttpResponse<String> login(Browser browser, String username, String password) throws Exception {
    return browser.mutate(
        "POST", "/api/auth/login", Map.of("username", username, "password", password));
  }

  URI uri(String path) {
    return URI.create("http://127.0.0.1:" + port + path);
  }

  class Browser {
    final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    final HttpClient client;

    Browser() throws Exception {
      client =
          HttpClient.newBuilder()
              .cookieHandler(cookies)
              .localAddress(InetAddress.getByName("127.0.0." + ADDRESS.getAndIncrement()))
              .build();
    }

    HttpResponse<String> get(String path) throws Exception {
      return client.send(
          HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> mutate(String method, String path, Object body) throws Exception {
      JsonNode csrf = json.readTree(get("/api/auth/csrf").body());
      return client.send(
          HttpRequest.newBuilder(uri(path))
              .header("Content-Type", "application/json")
              .header("X-CSRF-TOKEN", csrf.path("token").asText())
              .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
              .build(),
          HttpResponse.BodyHandlers.ofString());
    }
  }
}
