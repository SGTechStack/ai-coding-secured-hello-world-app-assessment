package com.example.demo_app.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/**
 * The {@code dev} profile that local runs and the e2e suite use: the demo user is seeded, and the
 * session cookie drops {@code Secure} so it works over plain-HTTP localhost, and the console logs
 * plain text rather than ECS JSON.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class DevProfileSessionCookieTest {

  @LocalServerPort private int port;

  @Autowired private RestClient.Builder restClientBuilder;

  @Autowired private Environment environment;

  @Test
  void demoUserCanLogInAndTheSessionCookieIsNotSecure() {
    String sessionCookie = SpaAuthFlow.logInAndGetSessionCookie(restClientBuilder, port);

    assertThat(sessionCookie)
        .containsIgnoringCase("HttpOnly")
        .contains("SameSite=Strict")
        .doesNotContainIgnoringCase("Secure");
  }

  @Test
  void consoleLoggingIsNotStructured() {
    assertThat(environment.getProperty("logging.structured.format.console")).isEmpty();
  }
}
