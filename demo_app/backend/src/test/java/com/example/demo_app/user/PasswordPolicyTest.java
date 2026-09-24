package com.example.demo_app.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** The shared password rules: length in characters and UTF-8 bytes, and the common list. */
class PasswordPolicyTest {

  private final PasswordPolicy policy = new PasswordPolicy();

  @Test
  void acceptsTwelveToSixtyFourCharacters() {
    assertThat(policy.problem("correct-hors")).isEmpty();
    assertThat(policy.problem("x".repeat(64))).isEmpty();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"eleven-char", "Sh0rt!"})
  void rejectsFewerThanTwelveCharacters(String password) {
    assertThat(policy.problem(password)).contains(PasswordPolicy.LENGTH_MESSAGE);
  }

  @Test
  void rejectsMoreThanSixtyFourCharacters() {
    assertThat(policy.problem("x".repeat(65))).contains(PasswordPolicy.LENGTH_MESSAGE);
  }

  @Test
  void countsCharactersNotUtf16Units() {
    // 12 emoji are 24 UTF-16 units and 48 UTF-8 bytes, but 12 characters.
    assertThat(policy.problem("🔒".repeat(12))).isEmpty();
    assertThat(policy.problem("🔒".repeat(11))).contains(PasswordPolicy.LENGTH_MESSAGE);
  }

  @Test
  void rejectsMoreThanSeventyTwoUtf8Bytes() {
    // "é" is 2 bytes: 36 of them are exactly BCrypt's 72-byte limit, 37 are one over.
    assertThat(policy.problem("é".repeat(36))).isEmpty();
    assertThat(policy.problem("é".repeat(37))).contains(PasswordPolicy.BYTES_MESSAGE);
  }

  @ParameterizedTest
  @ValueSource(strings = {"unbelievable", "UNBELIEVABLE", "UnBeLiEvAbLe", "scandinavian"})
  void rejectsCommonPasswordsWhateverTheirCase(String password) {
    assertThat(policy.problem(password)).contains(PasswordPolicy.COMMON_MESSAGE);
  }

  @Test
  void hasNoCompositionRules() {
    assertThat(policy.problem("all lowercase words here")).isEmpty();
  }

  @Test
  void neverEchoesThePassword() {
    String password = "unbelievable";
    Optional<String> problem = policy.problem(password);
    assertThat(problem).isPresent();
    assertThat(problem.get()).doesNotContainIgnoringCase(password);
  }
}
