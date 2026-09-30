package com.example.hello.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AuditLoggerTest {

  @Test
  void sanitizeStripsLineBreaksAndSpaces() {
    assertThat(AuditLogger.sanitize("evil\r\nevent=LOGIN_SUCCESS user=admin")).doesNotContain("\n").doesNotContain(" ");
    assertThat(AuditLogger.sanitize(null)).isEqualTo("-");
    assertThat(AuditLogger.sanitize("")).isEqualTo("-");
    assertThat(AuditLogger.sanitize("x".repeat(500))).hasSize(128);
  }

  @Test
  void rejectsOddKeyValueCount() {
    assertThatThrownBy(() -> new AuditLogger().event("X", "onlyKey")).isInstanceOf(IllegalArgumentException.class);
  }
}
