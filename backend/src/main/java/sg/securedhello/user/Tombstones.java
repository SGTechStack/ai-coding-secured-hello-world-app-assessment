package sg.securedhello.user;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import sg.securedhello.config.ApplicationKeys;

/**
 * Whether a canonical identifier is held by a deleted-user tombstone (ADR-044). The username is kept in plaintext; the
 * email only as lowercase hex HMAC-SHA-256 of its canonical form under the tombstone key (ADR-052). There is one
 * retained key version today, so one HMAC per lookup.
 */
@Component
public class Tombstones {

    private static final String HMAC = "HmacSHA256";

    private final DeletedUserRepository deleted;
    private final SecretKeySpec key;

    Tombstones(DeletedUserRepository deleted, ApplicationKeys keys) {
        this.deleted = deleted;
        this.key = new SecretKeySpec(keys.tombstoneHmac().bytes(), HMAC);
    }

    /** Whether a tombstone holds {@code username}, which must be canonical. */
    public boolean holdsUsername(String username) {
        return deleted.existsByUsername(username);
    }

    /** Whether a tombstone holds {@code canonicalEmail}, compared by its keyed hash. */
    public boolean holdsEmail(String canonicalEmail) {
        return deleted.existsByEmailHmac(emailHmac(canonicalEmail));
    }

    /** The stored form of a canonical email in {@code deleted_users.email_hmac}. */
    public String emailHmac(String canonicalEmail) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(canonicalEmail.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 over a validated 32-byte key cannot fail", e);
        }
    }
}
