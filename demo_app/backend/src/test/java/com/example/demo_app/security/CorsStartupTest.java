package com.example.demo_app.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo_app.DemoAppApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** Production has no default allow-list: without one, the API refuses to start. */
class CorsStartupTest {

  @Test
  void prodStartupFailsWithoutAllowedOrigins() {
    SpringApplicationBuilder app =
        new SpringApplicationBuilder(DemoAppApplication.class).profiles("prod");

    // Command-line args, not builder properties: those are defaults, which application.yml would
    // override with the shared test database that other contexts have already seeded.
    assertThatThrownBy(
            () ->
                app.run(
                    "--server.port=0",
                    "--spring.datasource.url=jdbc:h2:mem:cors-startup-test;DB_CLOSE_DELAY=-1"))
        .rootCause()
        .hasMessageContaining("app.cors.allowed-origins must list at least one origin");
  }
}
