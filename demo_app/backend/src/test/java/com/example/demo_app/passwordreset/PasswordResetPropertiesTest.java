package com.example.demo_app.passwordreset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo_app.DemoAppApplication;
import com.example.demo_app.passwordreset.PasswordResetProperties.PasswordReset;
import com.example.demo_app.passwordreset.PasswordResetProperties.Security;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** Reset links need a frontend URL and a positive TTL; without them the API refuses to start. */
class PasswordResetPropertiesTest {

  private static final Security TTL_30M = new Security(new PasswordReset(Duration.ofMinutes(30)));

  @Test
  void keepsAValidUrlWithoutTrailingSlashes() {
    PasswordResetProperties properties =
        new PasswordResetProperties("https://app.example.com/", TTL_30M);

    assertThat(properties.frontendUrl()).isEqualTo("https://app.example.com");
    assertThat(properties.tokenTtl()).isEqualTo(Duration.ofMinutes(30));
  }

  @Test
  void refusesAMissingOrNonHttpUrl() {
    for (String url : new String[] {null, "", "app.example.com", "javascript:alert(1)", "::"}) {
      assertThatThrownBy(() -> new PasswordResetProperties(url, TTL_30M))
          .hasMessageContaining("app.frontend-url");
    }
  }

  @Test
  void refusesAMissingOrNonPositiveTtl() {
    for (Security security :
        new Security[] {
          null,
          new Security(null),
          new Security(new PasswordReset(null)),
          new Security(new PasswordReset(Duration.ZERO))
        }) {
      assertThatThrownBy(() -> new PasswordResetProperties("http://localhost:3000", security))
          .hasMessageContaining("app.security.password-reset.token-ttl");
    }
  }

  @Test
  void prodStartupFailsWithoutAFrontendUrl() {
    SpringApplicationBuilder app =
        new SpringApplicationBuilder(DemoAppApplication.class).profiles("prod");

    // Command-line args, so they win over application.yml (see CorsStartupTest).
    assertThatThrownBy(
            () ->
                app.run(
                    "--server.port=0",
                    "--app.cors.allowed-origins=https://app.example.com",
                    "--spring.datasource.url=jdbc:h2:mem:reset-startup-test;DB_CLOSE_DELAY=-1"))
        .rootCause()
        .hasMessageContaining("app.frontend-url must be the SPA's http(s) origin");
  }
}
