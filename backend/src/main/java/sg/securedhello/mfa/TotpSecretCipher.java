package sg.securedhello.mfa;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.crypto.encrypt.BytesEncryptor;

import sg.securedhello.audit.AccountContext;
import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.audit.AuditEvent;

/**
 * Seals a TOTP secret into the 69-byte envelope both TOTP tables store, and opens it again (ADR-022; ADR-028).
 *
 * <p>The plaintext is a fixed-width <em>context prefix</em> followed by the secret: the user's UUID (16 raw bytes),
 * the key version (1 byte), then the secret (20 raw bytes), 37 bytes in all. {@code AesGcmBytesEncryptor} adds a
 * 16-byte IV and a 16-byte tag, so the envelope is 69 bytes. The context sits inside the plaintext, not in GCM
 * additional authenticated data, because {@link BytesEncryptor} takes none, and reaching AAD would mean a hand-built
 * {@code Cipher}, the code shape CVE-2026-47842 came from (ADR-028).
 *
 * <p>Keys form a ring keyed by version (ADR-022). New envelopes are sealed under the current version; an envelope is
 * opened under the key of the version its row stores, so rows sealed before a rotation keep opening while the old key
 * is retired but still configured. Re-sealing each row under the current key is the operator's rotation procedure
 * (R-CFG-005).
 *
 * <p>Opening compares the prefix with the row it came from, on fixed offsets. A mismatch means the ciphertext was
 * moved between users or replayed across key versions: a security event, audited at ERROR, not a decrypt error
 * (R-MFA-020).
 */
public class TotpSecretCipher {

    /** Raw secret bytes: 160 bits, RFC 4226's recommended length for HMAC-SHA1. */
    public static final int SECRET_BYTES = 20;

    /** The context prefix: the user's UUID and the key version. */
    static final int PREFIX_BYTES = 16 + 1;

    /** The stored envelope: IV, the ciphertext of prefix and secret, tag. Pinned by the schema's width checks. */
    public static final int ENVELOPE_BYTES = 16 + PREFIX_BYTES + SECRET_BYTES + 16;

    private final Map<Integer, BytesEncryptor> ring;
    private final int keyVersion;
    private final AuditEmitter audit;

    /**
     * @param ring       AES-256-GCM under each configured TOTP key, by its version (0..255)
     * @param keyVersion the current version, written into every new prefix and row; must be in {@code ring}
     * @param audit      where a prefix mismatch is reported
     */
    public TotpSecretCipher(Map<Integer, BytesEncryptor> ring, int keyVersion, AuditEmitter audit) {
        if (!ring.containsKey(keyVersion)) {
            throw new IllegalArgumentException("No TOTP key for the current version " + keyVersion);
        }
        this.ring = Map.copyOf(ring);
        this.keyVersion = keyVersion;
        this.audit = audit;
    }

    /** The version new envelopes are written under. */
    public int keyVersion() {
        return keyVersion;
    }

    /** The envelope sealing {@code secret} to {@code userId} under the current key version. */
    public byte[] seal(UUID userId, byte[] secret) {
        byte[] plaintext = ByteBuffer.allocate(PREFIX_BYTES + SECRET_BYTES)
                .put(prefix(userId, keyVersion))
                .put(secret)
                .array();
        return ring.get(keyVersion).encrypt(plaintext);
    }

    /**
     * The secret inside {@code envelope}, read from the row of {@code userId} stored under {@code rowKeyVersion},
     * opened under that version's key.
     *
     * @throws UnknownTotpKeyVersionException when no configured key has {@code rowKeyVersion}
     * @throws TotpContextMismatchException   when the prefix names another user or key version, after writing the
     *                                        mismatch row
     */
    public byte[] open(UUID userId, int rowKeyVersion, byte[] envelope) {
        BytesEncryptor encryptor = ring.get(rowKeyVersion);
        if (encryptor == null) {
            throw new UnknownTotpKeyVersionException(rowKeyVersion);
        }
        byte[] plaintext = encryptor.decrypt(envelope);
        if (plaintext.length != PREFIX_BYTES + SECRET_BYTES || !MessageDigest.isEqual(
                Arrays.copyOfRange(plaintext, 0, PREFIX_BYTES), prefix(userId, rowKeyVersion))) {
            audit.emit(AuditEvent.TOTP_CONTEXT_MISMATCH, AccountContext.of(userId));
            throw new TotpContextMismatchException();
        }
        return Arrays.copyOfRange(plaintext, PREFIX_BYTES, plaintext.length);
    }

    private static byte[] prefix(UUID userId, int keyVersion) {
        return ByteBuffer.allocate(PREFIX_BYTES)
                .putLong(userId.getMostSignificantBits())
                .putLong(userId.getLeastSignificantBits())
                .put((byte) keyVersion)
                .array();
    }

    /**
     * A row names a key version with no configured key: its key was dropped before the row was re-sealed. An operator
     * error in the rotation (R-CFG-005), answered as an internal error; the message names the version only.
     */
    public static final class UnknownTotpKeyVersionException extends IllegalStateException {

        UnknownTotpKeyVersionException(int version) {
            super("No TOTP key is configured for key version " + version);
        }
    }

    /** A TOTP envelope was opened against a row it was not sealed for (R-MFA-020). Answered as an internal error. */
    public static final class TotpContextMismatchException extends IllegalStateException {

        TotpContextMismatchException() {
            super("TOTP envelope context prefix does not match its row");
        }
    }
}
