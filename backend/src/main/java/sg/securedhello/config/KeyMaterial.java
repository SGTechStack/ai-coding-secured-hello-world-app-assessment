package sg.securedhello.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

/**
 * One validated 32-byte application key (ADR-022; ADR-052; ADR-054). Built only by {@link #decode}, after binding, so
 * the decoded bytes never take part in binding or in a failure report (ADR-062).
 *
 * <p>Neither {@link #toString()} nor any exception carries the key or its encoded form; a failure states the property
 * name and, at most, the observed length.
 */
public final class KeyMaterial {

    /** Every key is exactly 32 decoded bytes (R-CFG-017). */
    public static final int LENGTH = 32;

    private static final byte[] FINGERPRINT_DOMAIN =
            "secured-hello:key-fingerprint:v1:".getBytes(StandardCharsets.US_ASCII);

    private final String property;
    private final Integer version;
    private final byte[] bytes;

    private KeyMaterial(String property, Integer version, byte[] bytes) {
        this.property = property;
        this.version = version;
        this.bytes = bytes;
    }

    /**
     * Decodes and checks {@code encoded}: strict padded Base64, exactly 32 bytes, not all printable ASCII (an encoded
     * string rather than random bytes) and not all identical bytes (R-CFG-008).
     *
     * @throws InvalidKeyMaterialException naming {@code property}, never echoing the value
     */
    public static KeyMaterial decode(String property, Integer version, String encoded) {
        byte[] decoded = strictBase64(property, encoded);
        if (decoded.length != LENGTH) {
            throw new InvalidKeyMaterialException(property,
                    "decodes to " + decoded.length + " bytes; exactly " + LENGTH + " are required");
        }
        if (allPrintableAscii(decoded)) {
            throw new InvalidKeyMaterialException(property,
                    "decodes to printable text, not random bytes; generate it with: openssl rand -base64 32");
        }
        if (allIdentical(decoded)) {
            throw new InvalidKeyMaterialException(property, "decodes to 32 identical bytes");
        }
        return new KeyMaterial(property, version, decoded);
    }

    /** The property the key was bound from. */
    public String property() {
        return property;
    }

    /** The key's configured version, or {@code null} for an unversioned key. */
    public Integer version() {
        return version;
    }

    /** A copy of the 32 key bytes. */
    public byte[] bytes() {
        return bytes.clone();
    }

    /** 8 lowercase hex characters of a domain-separated SHA-256 digest of the key bytes (R-CFG-022). */
    public String fingerprint() {
        MessageDigest digest = sha256();
        digest.update(FINGERPRINT_DOMAIN);
        return HexFormat.of().formatHex(digest.digest(bytes), 0, 4);
    }

    /** True when both keys hold the same bytes, compared in constant time. */
    public boolean sameMaterialAs(KeyMaterial other) {
        return MessageDigest.isEqual(bytes, other.bytes);
    }

    @Override
    public String toString() {
        return "KeyMaterial[" + property + ", version=" + version + ", fingerprint=" + fingerprint() + "]";
    }

    /** The JDK decoder accepts missing padding, so padded length is checked first. */
    private static byte[] strictBase64(String property, String encoded) {
        if (encoded.length() % 4 == 0) {
            try {
                return Base64.getDecoder().decode(encoded);
            } catch (IllegalArgumentException e) {
                // Reported below, without the value or the decoder's message, which may quote it.
            }
        }
        throw new InvalidKeyMaterialException(property, "is not strict padded Base64");
    }

    private static boolean allPrintableAscii(byte[] decoded) {
        for (byte b : decoded) {
            if (b < 0x20 || b > 0x7e) {
                return false;
            }
        }
        return true;
    }

    private static boolean allIdentical(byte[] decoded) {
        for (byte b : decoded) {
            if (b != decoded[0]) {
                return false;
            }
        }
        return true;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is mandatory on every Java platform", e);
        }
    }
}
