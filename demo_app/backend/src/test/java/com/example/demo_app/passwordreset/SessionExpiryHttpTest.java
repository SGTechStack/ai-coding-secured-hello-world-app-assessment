package com.example.demo_app.passwordreset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.HttpCookie;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/**
 * A password reset signs the user out everywhere, observed through the servlet container (real
 * sessions, cookies and session events), which MockMvc does not run: the old session's next
 * request is the JSON {@code 401}, never a redirect, and its cookie is expired.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class SessionExpiryHttpTest {

  private static final Pattern TOKEN = Pattern.compile("reset-password#token=([A-Za-z0-9_-]+)");

  @LocalServerPort private int port;
  @Autowired private RestClient.Builder restClientBuilder;

  @Test
  void anExpiredSessionGetsTheJson401AndAnExpiredCookie(CapturedOutput output) {
    RestClient client =
        restClientBuilder
            .baseUrl("http://localhost:" + port)
            .defaultStatusHandler(status -> true, (request, response) -> {})
            .build();
    String name = "pwr-http-" + UUID.randomUUID().toString().substring(0, 8);
    String password = "correct horse battery";

    post(
        client,
        "/api/v1/auth/register",
        null,
        """
        {"username": "%s", "email": "%s@example.com", "firstName": "Ada", "password": "%s"}
        """
            .formatted(name, name, password),
        201);
    ResponseEntity<String> login =
        post(
            client,
            "/api/v1/auth/login",
            null,
            """
            {"username": "%s", "password": "%s"}
            """
                .formatted(name, password),
            200);
    String session = "JSESSIONID=" + cookie(login, "JSESSIONID");
    assertThat(me(client, session).getStatusCode().value()).isEqualTo(200);

    post(
        client,
        "/api/v1/auth/password-reset/request",
        null,
        "{\"email\": \"%s@example.com\"}".formatted(name),
        202);
    // The link is emailed off the request thread.
    await()
        .atMost(Duration.ofSeconds(5))
        .until(() -> output.getOut().contains("Password reset email to " + name));
    Matcher token =
        TOKEN.matcher(
            output
                .getOut()
                .lines()
                .filter(line -> line.contains("Password reset email to " + name))
                .reduce((first, last) -> last)
                .orElseThrow());
    assertThat(token.find()).isTrue();
    post(
        client,
        "/api/v1/auth/password-reset/confirm",
        null,
        "{\"token\": \"%s\", \"newPassword\": \"stapled paper clip\"}".formatted(token.group(1)),
        204);

    ResponseEntity<String> expired = me(client, session);
    assertThat(expired.getStatusCode().value()).isEqualTo(401);
    assertThat(expired.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    assertThat(expired.getHeaders().getLocation()).isNull();
    assertThat(expired.getBody()).contains("\"code\":\"UNAUTHORIZED\"");
    assertThat(cookieHeader(expired, "JSESSIONID")).contains("Max-Age=0");
    // Ending an expired session is not a logout.
    assertThat(output.getOut()).doesNotContain("event=LOGOUT actor=" + name);
    // And the same session stays ended.
    assertThat(me(client, session).getStatusCode().value()).isEqualTo(401);
  }

  private static ResponseEntity<String> me(RestClient client, String sessionCookie) {
    return client
        .get()
        .uri("/api/v1/auth/me")
        .header(HttpHeaders.COOKIE, sessionCookie)
        .retrieve()
        .toEntity(String.class);
  }

  /** A CSRF-protected JSON POST, as the SPA sends it; asserts the status. */
  private static ResponseEntity<String> post(
      RestClient client, String path, String sessionCookie, String body, int expectedStatus) {
    ResponseEntity<String> csrf =
        client.get().uri("/api/v1/auth/csrf").retrieve().toEntity(String.class);
    String xsrf = cookie(csrf, "XSRF-TOKEN");
    String cookies = "XSRF-TOKEN=" + xsrf + (sessionCookie == null ? "" : "; " + sessionCookie);
    ResponseEntity<String> response =
        client
            .post()
            .uri(path)
            .header(HttpHeaders.COOKIE, cookies)
            .header("X-XSRF-TOKEN", xsrf)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .toEntity(String.class);
    assertThat(response.getStatusCode().value()).as("POST %s", path).isEqualTo(expectedStatus);
    return response;
  }

  private static String cookie(ResponseEntity<?> response, String name) {
    return HttpCookie.parse(cookieHeader(response, name)).getFirst().getValue();
  }

  private static String cookieHeader(ResponseEntity<?> response, String name) {
    List<String> cookies = response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE);
    return cookies.stream()
        .filter(c -> c.startsWith(name + "="))
        .findFirst()
        .orElseThrow(() -> new AssertionError(name + " cookie not set; got " + cookies));
  }
}
