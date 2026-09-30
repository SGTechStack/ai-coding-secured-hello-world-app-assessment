package local.builderday.common.audit;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.HKDFParameters;

/**
 * The keyed Session hash behind every {@code session.hash} (Security audit events and the Request log): lowercase hex
 * HMAC-SHA256 of the Session identifier. The key is derived with HKDF-SHA256 from a system-wide secret (the datasource
 * password) under a fixed, versioned label, so someone with only log access cannot confirm a leaked session identifier
 * or link hashes from another system (OWASP Session Management: log a salted hash). The key lives in memory only.
 */
public final class SessionIds {
  static final String CONTEXT_LABEL = "builderday/session-hash/v1";
  private static final String HMAC = "HmacSHA256";
  private static final int KEY_BYTES = 32;

  // ponytail: a process-wide key so the static SecurityAudit can hash without becoming a bean. Every instance derives
  // the same key from the same secret, so the last one installed wins harmlessly; make SecurityAudit a bean to drop it.
  private static volatile SessionIds installed;

  private final SecretKeySpec key;

  /** @throws IllegalStateException when {@code secret} is blank: an empty input would make the key public */
  public SessionIds(String secret) {
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException("spring.datasource.password must not be blank: it keys the Session hash.");
    }
    byte[] derived = new byte[KEY_BYTES];
    var hkdf = new HKDFBytesGenerator(new SHA256Digest());
    hkdf.init(new HKDFParameters(secret.getBytes(StandardCharsets.UTF_8), null,
        CONTEXT_LABEL.getBytes(StandardCharsets.UTF_8)));
    hkdf.generateBytes(derived, 0, KEY_BYTES);
    key = new SecretKeySpec(derived, HMAC);
  }

  /** Makes {@code sessionIds} the key {@link #hashCurrent} uses. */
  public static void install(SessionIds sessionIds) { installed = sessionIds; }

  /** @return the keyed hash of {@code sessionId}, or {@code null} for {@code null} */
  public String hash(String sessionId) {
    if (sessionId == null) return null;
    try {
      var mac = Mac.getInstance(HMAC);
      mac.init(key);
      return HexFormat.of().formatHex(mac.doFinal(sessionId.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("HMAC-SHA256 is unavailable.", exception);
    }
  }

  /**
   * @return the keyed hash of the request's current Session, or {@code null} when it has none; never creates one
   * @throws IllegalStateException before a key is installed
   */
  public static String hashCurrent(HttpServletRequest request) {
    var session = request.getSession(false);
    if (session == null) return null;
    var sessionIds = installed;
    if (sessionIds == null) throw new IllegalStateException("No Session hash key is installed.");
    return sessionIds.hash(session.getId());
  }

  /**
   * Lowercase hex SHA-256 of {@code value}'s UTF-8 bytes, unkeyed; for long random values looked up by digest (reset
   * tokens) only, never for passwords or Session identifiers.
   */
  public static String sha256Hex(String value) {
    try {
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable.", exception);
    }
  }
}
