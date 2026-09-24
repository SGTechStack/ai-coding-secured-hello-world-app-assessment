package com.example.demo_app.auth;

import static com.example.demo_app.auth.SpaAuthFlow.logInAndGetSessionCookie;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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
}
