package org.eds.demo.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StringUtilsTest {

  @Test
  void capitalizeFirstLetterCapitalizesFirstCharacter() {
    assertThat(StringUtils.capitalizeFirstLetter("hello")).isEqualTo("Hello");
    assertThat(StringUtils.capitalizeFirstLetter("world")).isEqualTo("World");
  }

  @Test
  void capitalizeFirstLetterHandlesMultipleWords() {
    assertThat(StringUtils.capitalizeFirstLetter("hello world")).isEqualTo("Hello world");
    assertThat(StringUtils.capitalizeFirstLetter("current user profile"))
      .isEqualTo("Current user profile");
    assertThat(StringUtils.capitalizeFirstLetter("application settings page"))
      .isEqualTo("Application settings page");
  }

  @Test
  void capitalizeFirstLetterTrimsWhitespace() {
    assertThat(StringUtils.capitalizeFirstLetter("  hello  ")).isEqualTo("Hello");
    assertThat(StringUtils.capitalizeFirstLetter("  world")).isEqualTo("World");
    assertThat(StringUtils.capitalizeFirstLetter("  hello world  ")).isEqualTo("Hello world");
  }

  @Test
  void capitalizeFirstLetterHandlesAlreadyCapitalized() {
    assertThat(StringUtils.capitalizeFirstLetter("Hello")).isEqualTo("Hello");
    assertThat(StringUtils.capitalizeFirstLetter("World")).isEqualTo("World");
    assertThat(StringUtils.capitalizeFirstLetter("Hello World")).isEqualTo("Hello World");
  }

  @Test
  void capitalizeFirstLetterHandlesSingleCharacter() {
    assertThat(StringUtils.capitalizeFirstLetter("a")).isEqualTo("A");
    assertThat(StringUtils.capitalizeFirstLetter("Z")).isEqualTo("Z");
  }

  @Test
  void capitalizeFirstLetterHandlesEmptyString() {
    assertThat(StringUtils.capitalizeFirstLetter("")).isEqualTo("");
    assertThat(StringUtils.capitalizeFirstLetter("   ")).isEqualTo("   ");
  }

  @Test
  void capitalizeFirstLetterHandlesNull() {
    assertThat(StringUtils.capitalizeFirstLetter(null)).isNull();
  }

  @Test
  void capitalizeFirstLetterHandlesSpecialCharacters() {
    assertThat(StringUtils.capitalizeFirstLetter("123abc")).isEqualTo("123abc");
    assertThat(StringUtils.capitalizeFirstLetter("!hello")).isEqualTo("!hello");
    assertThat(StringUtils.capitalizeFirstLetter("123 hello world")).isEqualTo("123 hello world");
  }
}
