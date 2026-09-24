package com.example.demo_app.user;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * The one password policy, shared by registration, password-reset confirm and admin bootstrap
 * (NIST 800-63B style, no composition rules):
 *
 * <ul>
 *   <li>12 to 64 characters (code points, so an emoji counts once);
 *   <li>at most 72 UTF-8 bytes, because BCrypt ignores anything past that;
 *   <li>not on the bundled top-10k common-password list, compared case-insensitively.
 * </ul>
 *
 * <p>Its messages are fixed text: they never contain the password, so they are safe to return in
 * an API error or a startup failure. HTTP request bodies check it through {@link
 * AcceptablePassword}.
 */
@Component
public class PasswordPolicy {

  public static final int MIN_LENGTH = 12;
  public static final int MAX_LENGTH = 64;
  public static final int MAX_BYTES = 72;

  static final String LENGTH_MESSAGE = "Password must be 12 to 64 characters.";
  static final String BYTES_MESSAGE =
      "Password is too long. Use fewer accented letters, symbols or emoji.";
  static final String COMMON_MESSAGE = "This password is too common. Choose a less guessable one.";

  private static final String COMMON_PASSWORDS = "security/common-passwords.txt";

  private final Set<String> commonPasswords;

  public PasswordPolicy() {
    this.commonPasswords = loadCommonPasswords();
  }

  /** What is wrong with {@code password}, or empty if it meets the policy. {@code null} fails. */
  public Optional<String> problem(String password) {
    String value = password == null ? "" : password;
    int length = value.codePointCount(0, value.length());
    if (length < MIN_LENGTH || length > MAX_LENGTH) {
      return Optional.of(LENGTH_MESSAGE);
    }
    if (value.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
      return Optional.of(BYTES_MESSAGE);
    }
    if (commonPasswords.contains(value.toLowerCase(Locale.ROOT))) {
      return Optional.of(COMMON_MESSAGE);
    }
    return Optional.empty();
  }

  /** The bundled list, lower-cased; {@code #} lines are its attribution header. */
  private static Set<String> loadCommonPasswords() {
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(
                new ClassPathResource(COMMON_PASSWORDS).getInputStream(),
                StandardCharsets.UTF_8))) {
      return reader
          .lines()
          .filter(line -> !line.isBlank() && !line.startsWith("#"))
          .map(line -> line.toLowerCase(Locale.ROOT))
          .collect(Collectors.toUnmodifiableSet());
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read " + COMMON_PASSWORDS, e);
    }
  }
}
