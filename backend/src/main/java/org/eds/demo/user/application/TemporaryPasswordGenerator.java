package org.eds.demo.user.application;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** Generates Temporary Passwords from a cryptographically secure random source. */
@Component
class TemporaryPasswordGenerator {

  /** Omits look-alike characters (0/O, 1/l/I) because admins relay the value to a person. */
  private static final String ALPHABET =
      "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

  /** 20 characters of a 57-symbol alphabet is roughly 116 bits of entropy. */
  private static final int LENGTH = 20;

  private final SecureRandom random = new SecureRandom();

  String generate() {
    var password = new StringBuilder(LENGTH);
    for (int i = 0; i < LENGTH; i++) {
      password.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
    }
    return password.toString();
  }
}
