package sg.securedhello.mfa;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.OptionalLong;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The TOTP code check (RFC 6238; spec, Second factor): HMAC-SHA1, six digits, a 30-second step and one step of skew
 * either way (R-MFA-017). A pure function of the secret, the submitted code, the instant and the last accepted
 * counter, so it is unit-tested and mutation-tested without a context. A code is accepted only for a counter after
 * {@code lastUsedCounter}, so a verified code can never verify again (RFC 6238 §5.2).
 */
public final class TotpWindow {

    /** The time step, in seconds (RFC 6238 §4.1, X). */
    public static final long STEP_SECONDS = 30;

    /** The production code length. */
    public static final int DIGITS = 6;

    /** Accepted steps either side of the current one. */
    static final int SKEW = 1;

    /** {@code lastUsedCounter} for a secret that has never verified a code. */
    public static final long NEVER_USED = -1;

    private static final int[] POWERS_OF_TEN = {1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000,
        100_000_000};

    private TotpWindow() {
    }

    /** The time-step counter at {@code instant}: whole 30-second steps since the epoch (RFC 6238 §4.2, T). */
    public static long counter(Instant instant) {
        return Math.floorDiv(instant.getEpochSecond(), STEP_SECONDS);
    }

    /**
     * The counter {@code code} was generated for, if it is a six-digit code for {@code secret} at a counter within
     * one step of {@code now} and after {@code lastUsedCounter}; otherwise empty. Anything but exactly six ASCII
     * digits never matches.
     */
    public static OptionalLong match(byte[] secret, String code, Instant now, long lastUsedCounter) {
        // Compared whole against six generated digits, so anything but exactly six ASCII digits never matches.
        byte[] submitted = code.getBytes(StandardCharsets.US_ASCII);
        long current = counter(now);
        for (long counter = current - SKEW; counter <= current + SKEW; counter++) {
            byte[] expected = generate(secret, counter, DIGITS).getBytes(StandardCharsets.US_ASCII);
            if (counter > lastUsedCounter && MessageDigest.isEqual(expected, submitted)) {
                return OptionalLong.of(counter);
            }
        }
        return OptionalLong.empty();
    }

    /** The {@code digits}-digit code for {@code secret} at {@code counter} (RFC 6238 §4.2; RFC 4226 §5.3). */
    public static String generate(byte[] secret, long counter, int digits) {
        byte[] hash = hmacSha1(secret, counter);
        int offset = hash[hash.length - 1] & 0x0f;
        int binary = (hash[offset] & 0x7f) << 24
                | (hash[offset + 1] & 0xff) << 16
                | (hash[offset + 2] & 0xff) << 8
                | (hash[offset + 3] & 0xff);
        String value = Integer.toString(binary % POWERS_OF_TEN[digits]);
        return "0".repeat(digits - value.length()) + value;
    }

    private static byte[] hmacSha1(byte[] secret, long counter) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            return mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(counter).array());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA1 is unavailable", ex);
        }
    }
}
