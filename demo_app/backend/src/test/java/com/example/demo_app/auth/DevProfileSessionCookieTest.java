package com.example.demo_app.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/**
 * The {@code dev} profile that local runs and the e2e suite use: the demo user is seeded, and the
 * session cookie drops {@code Secure} so it works over plain-HTTP localhost.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class DevProfileSessionCookieTest {

  @LocalServerPort private int port;

  @Autowired private RestClient.Builder restClientBuilder;

  @Test
  void demoUserCanLogInAndTheSessionCookieIsNotSecure() {
    String sessionCookie = SpaAuthFlow.logInAndGetSessionCookie(restClientBuilder, port);

    assertThat(sessionCookie)
        .containsIgnoringCase("HttpOnly")
        .contains("SameSite=Strict")
        .doesNotContainIgnoringCase("Secure");
  }
}
