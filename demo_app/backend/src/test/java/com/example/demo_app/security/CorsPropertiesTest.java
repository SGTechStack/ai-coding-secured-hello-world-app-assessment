package com.example.demo_app.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CorsPropertiesTest {

  @Test
  void acceptsExactOrigins() {
    CorsProperties properties =
        new CorsProperties(List.of("http://localhost:3000", "https://app.example.com"));

    assertThat(properties.allowedOrigins())
        .containsExactly("http://localhost:3000", "https://app.example.com");
  }

  @Test
  void rejectsAMissingList() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new CorsProperties(null))
        .withMessageContaining("app.cors.allowed-origins must list at least one origin");
  }

  @Test
  void rejectsAnEmptyList() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new CorsProperties(List.of()))
        .withMessageContaining("app.cors.allowed-origins must list at least one origin");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "*",
        "null",
        "",
        " ",
        "https://*.example.com",
        "localhost:3000",
        "ftp://example.com",
        "https://example.com/",
        "https://example.com/app",
        "https://example.com?x=1",
        "https://example.com#x",
        "https://user@example.com",
        "https://example.com:3000:1"
      })
  void rejectsAnythingButAnExactOrigin(String origin) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new CorsProperties(List.of("http://localhost:3000", origin)))
        .withMessageContaining("app.cors.allowed-origins");
  }
}
