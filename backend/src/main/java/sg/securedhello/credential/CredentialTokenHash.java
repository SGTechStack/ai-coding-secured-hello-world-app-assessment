package sg.securedhello.credential;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * A credential token's plaintext and its stored form (ADR-007), as pure functions:
 * <ul>
 *   <li>a token is 256 bits from {@code SecureRandom}, Base64url without padding: 43 characters;</li>
 *   <li>its stored form is lowercase hex {@code SHA-256(type_label || ":" || token)}. The type label separates the
 *       domains, so a token of one type is simply not found as another, and the Base64url alphabet has no {@code :}
 *       to spoof the separator with.</li>
 * </ul>
 */
public final class CredentialTokenHash {

    /** Bytes of entropy in a token. */
    static final int TOKEN_BYTES = 32;

    /** The only shape a minted token can have: 43 Base64url characters. */
    private static final Pattern WELL_FORMED = Pattern.compile("[A-Za-z0-9_-]{43}");

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private CredentialTokenHash() {
    }

    /** A fresh token: {@value #TOKEN_BYTES} random bytes, Base64url without padding. */
    public static String generate(SecureRandom random) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    /** Whether {@code submitted} has the shape of a minted token; anything else cannot match a stored hash. */
    public static boolean wellFormed(String submitted) {
        return WELL_FORMED.matcher(submitted).matches();
    }

    /** The stored form of {@code token} as a {@code type} token: lowercase hex SHA-256 of {@code TYPE:token}. */
    public static String hash(CredentialTokenType type, String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((type.name() + ":" + token).getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is mandatory on every Java platform", e);
        }
    }
}
