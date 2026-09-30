package com.example.hello.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.hello.common.PasswordPolicyException;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

  private final PasswordPolicy policy = new PasswordPolicy();

  @Test
  void acceptsTwelveCharacters() {
    assertThat(policy.violations("abcdefghijkl", "alice")).isEmpty();
  }

  @Test
  void rejectsElevenCharacters() {
    assertThat(policy.violations("abcdefghijk", "alice")).hasSize(1);
  }

  @Test
  void rejectsNull() {
    assertThat(policy.violations(null, "alice")).hasSize(1);
  }

  @Test
  void rejectsOverBcryptLimit() {
    assertThat(policy.violations("x".repeat(73), "alice")).isNotEmpty();
  }

  @Test
  void rejectsPasswordContainingUsername() {
    assertThat(policy.violations("Alice-is-great-2026", "alice")).containsExactly("must not contain the username");
  }

  @Test
  void validateThrowsWithFieldErrors() {
    assertThatThrownBy(() -> policy.validate("short", "bob"))
        .isInstanceOf(PasswordPolicyException.class)
        .satisfies(ex -> assertThat(((PasswordPolicyException) ex).getProperties()).containsKey("errors"));
  }
}
