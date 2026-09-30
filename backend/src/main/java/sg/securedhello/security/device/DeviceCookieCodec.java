package sg.securedhello.security.device;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The device cookie's value: a trusted device's id and an HMAC-SHA256 over it (ADR-075; OWASP "Slow Down Online
 * Guessing Attacks with Device Cookies"). Pure: no state beyond the key, no lookups.
 *
 * <p>The value is {@code base64url(id) "." base64url(mac)}, unpadded, where {@code mac} is the HMAC of a fixed domain
 * label and the id's 16 bytes. The id alone is unguessable (UUIDv4), but the HMAC means a value that was not issued here
 * is refused before any lookup, and that a reader of the database, which holds the ids, still cannot build a cookie.
 * The binding to an account is the device row's, not the cookie's: the row names the account, so a cookie never carries
 * a username or account id.
 */
public final class DeviceCookieCodec {

    private static final String ALGORITHM = "HmacSHA256";
    private static final byte[] DOMAIN = "secured-hello:device-cookie:v1:".getBytes(StandardCharsets.US_ASCII);
    private static final int ID_BYTES = 16;
    private static final int MAC_BYTES = 32;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    /** The longest value {@link #verify} reads: a 22-character id, the dot and a 43-character MAC. */
    static final int VALUE_LENGTH = 22 + 1 + 43;

    private final SecretKeySpec key;

    /** @param key the 32-byte device-cookie key ({@code app.security.lockout.device.secret}) */
    public DeviceCookieCodec(byte[] key) {
        this.key = new SecretKeySpec(key, ALGORITHM);
    }

    /** The cookie value for {@code deviceId}. */
    public String sign(UUID deviceId) {
        byte[] id = bytes(deviceId);
        return ENCODER.encodeToString(id) + "." + ENCODER.encodeToString(mac(id));
    }

    /**
     * The device a cookie value names, if the value is exactly the shape {@link #sign} writes and its HMAC verifies,
     * compared in constant time. Anything else, including a value signed under another key, is empty.
     */
    public Optional<UUID> verify(String value) {
        int dot = value.indexOf('.');
        if (value.length() != VALUE_LENGTH || dot != 22) {
            return Optional.empty();
        }
        byte[] id;
        byte[] presented;
        try {
            id = DECODER.decode(value.substring(0, dot));
            presented = DECODER.decode(value.substring(dot + 1));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
        if (id.length != ID_BYTES || presented.length != MAC_BYTES || !MessageDigest.isEqual(mac(id), presented)) {
            return Optional.empty();
        }
        ByteBuffer buffer = ByteBuffer.wrap(id);
        return Optional.of(new UUID(buffer.getLong(), buffer.getLong()));
    }

    private byte[] mac(byte[] id) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            mac.update(DOMAIN);
            return mac.doFinal(id);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is mandatory on every Java platform", e);
        }
    }

    private static byte[] bytes(UUID id) {
        return ByteBuffer.allocate(ID_BYTES).putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits()).array();
    }
}
