package com.example.hello;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:secure;DB_CLOSE_DELAY=-1",
      "app.admin.username=operator", "app.admin.email=operator@example.com",
      "app.admin.password=Test-Operator-Password-2026"
    })
class ProductionCookieIntegrationTest extends HttpIntegrationSupport {
  @Test
  void defaultCookieIsSecureHttpOnlyAndSameSite() throws Exception {
    assertThat(new Browser().get("/api/auth/csrf").headers().firstValue("Set-Cookie").orElseThrow())
        .contains("Secure", "HttpOnly", "SameSite=Lax");
  }
}
