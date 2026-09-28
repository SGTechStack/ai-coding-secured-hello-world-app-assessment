package sg.securedhello.audit;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import sg.securedhello.security.source.SourceKey;

/**
 * The keyed correlation hashes (ADR-054): HMAC-SHA-256 under {@code app.security.hmac.log.key}, as 64 lowercase hex
 * characters. A key is what makes each value a pseudonym: an unkeyed hash of an address is a lookup table, and an
 * unkeyed hash of a session id lets anyone holding the cookie pick out that session's rows. Each input carries a
 * domain prefix, so a source key and a session id never hash alike.
 */
public final class LogFieldHasher {

    private static final String ALGORITHM = "HmacSHA256";

    /** The {@code source.ip_hash} domain prefix, pinned by T-AUD-041. */
    static final String SOURCE_PREFIX = "ip:";

    /** The {@code session.hash} domain prefix, pinned by T-AUD-042. */
    static final String SESSION_PREFIX = "session:";

    private final SecretKeySpec key;

    public LogFieldHasher(byte[] logKey) {
        this.key = new SecretKeySpec(logKey, ALGORITHM);
    }

    /** {@code source.ip_hash}: over the source key's text form, never the address. */
    public String sourceIpHash(SourceKey sourceKey) {
        return hmac(SOURCE_PREFIX + sourceKey.value());
    }

    /** {@code session.hash}: over the raw session id, which itself is never logged. */
    public String sessionHash(String sessionId) {
        return hmac(SESSION_PREFIX + sessionId);
    }

    private String hmac(String input) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is mandatory on every Java platform", e);
        }
    }

    @Override
    public String toString() {
        return "LogFieldHasher[key=<redacted>]";
    }
}
