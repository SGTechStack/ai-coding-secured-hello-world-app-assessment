package com.example.demo_app.auth;

import static com.example.demo_app.auth.SpaAuthFlow.csrfToken;
import static com.example.demo_app.auth.SpaAuthFlow.logInAndGetSessionCookie;
import static com.example.demo_app.auth.SpaAuthFlow.setCookie;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.HttpCookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/**
 * The {@code JSESSIONID} cookie is written by the servlet container, which MockMvc does not run,
 * so its attributes are asserted over real HTTP, following the SPA flow ({@link SpaAuthFlow}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SessionCookieTest {

  @LocalServerPort private int port;

  @Autowired private RestClient.Builder restClientBuilder;

  @Test
  void loginSetsHttpOnlySecureSameSiteStrictSessionCookie() {
    String sessionCookie = logInAndGetSessionCookie(restClientBuilder, port);

    assertThat(sessionCookie)
        .containsIgnoringCase("HttpOnly")
        .containsIgnoringCase("Secure")
        .contains("SameSite=Strict");
  }

  @Test
  void logoutExpiresTheSessionCookieWithItsOriginalAttributesAndEndsTheSession() {
    String sessionId =
        HttpCookie.parse(logInAndGetSessionCookie(restClientBuilder, port)).getFirst().getValue();
    RestClient client =
        restClientBuilder
            .baseUrl("http://localhost:" + port)
            .defaultStatusHandler(status -> true, (request, response) -> {})
            .build();

    String xsrfToken = csrfToken(client, "JSESSIONID=" + sessionId);

    ResponseEntity<Void> logout =
        client
            .post()
            .uri("/api/v1/auth/logout")
            .header(HttpHeaders.COOKIE, "JSESSIONID=" + sessionId + "; XSRF-TOKEN=" + xsrfToken)
            .header("X-XSRF-TOKEN", xsrfToken)
            .retrieve()
            .toBodilessEntity();

    assertThat(logout.getStatusCode().value()).isEqualTo(204);
    assertThat(setCookie(logout, "JSESSIONID"))
        .contains("Max-Age=0")
        .contains("Path=/")
        .containsIgnoringCase("HttpOnly")
        .containsIgnoringCase("Secure")
        .contains("SameSite=Strict");
    assertThat(setCookie(logout, "XSRF-TOKEN")).contains("Max-Age=0");

    ResponseEntity<Void> me =
        client
            .get()
            .uri("/api/v1/auth/me")
            .header(HttpHeaders.COOKIE, "JSESSIONID=" + sessionId)
            .retrieve()
            .toBodilessEntity();
    assertThat(me.getStatusCode().value()).isEqualTo(401);
  }
}
