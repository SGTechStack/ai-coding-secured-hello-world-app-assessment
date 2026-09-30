package com.assessment.auth.password;

import com.assessment.auth.common.ApiErrorCode;
import com.assessment.auth.common.ApiException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * The password policy (spec.md S5, ticket 04).
 *
 * <p>Min 12, max 72, full printable ASCII, a bundled denylist, and no substring of the username or
 * the email local part. <strong>No composition rules.</strong> A 12-character all-lowercase
 * passphrase with spaces must be accepted — Priv:109's regex is recipe-only, the standard's
 * one-of-each-class clause is scoped to the admin-generated password (now inapplicable, ticket 11),
 * and the recipe's own note says to remove it.
 *
 * <p>Enforced <strong>explicitly from all four write paths</strong> — registration, admin-create,
 * reset-confirm and self-service change — and deliberately not by Bean Validation, so that adding a
 * fifth write path without calling it is a visible omission rather than a missing annotation.
 *
 * <p>HIBP is <em>declined, not skipped</em>: a variable-latency third-party call on the
 * registration path would undermine Std:247's identical-timing clause.
 */
@Component
public class PasswordPolicy {

  private static final char MIN_PRINTABLE_ASCII = 0x20;
  private static final char MAX_PRINTABLE_ASCII = 0x7E;

  private final PasswordProperties properties;
  private final Set<String> denylist;

  public PasswordPolicy(PasswordProperties properties) {
    this.properties = properties;
    this.denylist = loadDenylist();
  }

  /**
   * Validates a candidate password, throwing with the violated rule named.
   *
   * @param username the account's username, rejected as a substring
   * @param email the account's email; its local part is rejected as a substring
   */
  public void validate(String password, String username, String email) {
    if (password == null || password.length() < properties.minLength()) {
      throw reject("Password must be at least " + properties.minLength() + " characters.");
    }
    if (password.length() > properties.maxLength()) {
      throw reject("Password must be at most " + properties.maxLength() + " characters.");
    }
    for (int i = 0; i < password.length(); i++) {
      char c = password.charAt(i);
      if (c < MIN_PRINTABLE_ASCII || c > MAX_PRINTABLE_ASCII) {
        throw reject("Password may contain printable ASCII characters only.");
      }
    }
    String lower = password.toLowerCase(Locale.ROOT);
    if (denylist.contains(lower)) {
      throw reject("Password is too common.");
    }
    if (username != null && !username.isBlank() && lower.contains(username.toLowerCase(Locale.ROOT))) {
      throw reject("Password must not contain the username.");
    }
    String localPart = emailLocalPart(email);
    if (localPart != null && !localPart.isBlank() && lower.contains(localPart.toLowerCase(Locale.ROOT))) {
      throw reject("Password must not contain the email local part.");
    }
  }

  private static String emailLocalPart(String email) {
    if (email == null) {
      return null;
    }
    int at = email.indexOf('@');
    return at < 0 ? email : email.substring(0, at);
  }

  private static ApiException reject(String detail) {
    return new ApiException(ApiErrorCode.VALIDATION_FAILED, detail);
  }

  private static Set<String> loadDenylist() {
    ClassPathResource resource = new ClassPathResource("password-denylist.txt");
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
      return reader
          .lines()
          .map(String::trim)
          .filter(line -> !line.isEmpty() && !line.startsWith("#"))
          .map(line -> line.toLowerCase(Locale.ROOT))
          .collect(Collectors.toUnmodifiableSet());
    } catch (IOException ex) {
      throw new UncheckedIOException("Bundled password denylist could not be read", ex);
    }
  }
}
