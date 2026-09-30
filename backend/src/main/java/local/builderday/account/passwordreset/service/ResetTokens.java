package local.builderday.account.passwordreset.service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;
import local.builderday.common.audit.SessionIds;

/**
 * Password reset token values (ADR 0004): 32 random bytes, base64url without padding, stored only as SHA-256. A fast
 * hash is enough because the token carries 256 bits of entropy, and it keeps lookup by hash possible.
 */
final class ResetTokens {
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final Pattern SHAPE = Pattern.compile("^[A-Za-z0-9_-]{43}$");

  private ResetTokens() {}

  static String generate() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** True for a value that could be a token; anything else is rejected before any lookup. */
  static boolean wellFormed(String token) {
    return token != null && SHAPE.matcher(token).matches();
  }

  /** Tokens are ASCII, so their UTF-8 digest is the ASCII digest. */
  static String hash(String token) { return SessionIds.sha256Hex(token); }
}
