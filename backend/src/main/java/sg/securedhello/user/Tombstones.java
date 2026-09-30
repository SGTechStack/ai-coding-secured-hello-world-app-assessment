package sg.securedhello.user;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import sg.securedhello.config.ApplicationKeys;

/**
 * Deleted-user tombstones (ADR-044): whether a canonical identifier is held by one, and the delete that leaves one. The
 * username is kept in plaintext; the email only as lowercase hex HMAC-SHA-256 of its canonical form under the tombstone
 * key (ADR-052). There is one retained key version today, so one HMAC per lookup, and new tombstones are written under
 * it: the key is versioned forward only, and a tombstone is never re-keyed.
 */
@Component
public class Tombstones {

    private static final String HMAC = "HmacSHA256";

    private final DeletedUserRepository deleted;
    private final UserAccountRepository accounts;
    private final SecretKeySpec key;
    private final Clock clock;

    Tombstones(DeletedUserRepository deleted, UserAccountRepository accounts, ApplicationKeys keys, Clock clock) {
        this.deleted = deleted;
        this.accounts = accounts;
        this.key = new SecretKeySpec(keys.tombstoneHmac().bytes(), HMAC);
        this.clock = clock;
    }

    /**
     * Deletes {@code account} and leaves its tombstone, in the caller's transaction, so both commit or neither does
     * (ADR-044). The tombstone is written first and the {@code users} row deleted after it; every child row goes with
     * the account by {@code ON DELETE CASCADE}, its password history included (REJ-034; REJ-035). Only
     * {@code AdminActions} calls it, after {@code AdminActionGuard} and under its lock set (ADR-048; ArchUnit).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteLeavingTombstone(UserAccount account, UUID deletedBy) {
        deleted.saveAndFlush(new DeletedUser(account.getId(), account.getUsername(), emailHmac(account.getEmail()),
                clock.instant(), deletedBy));
        accounts.delete(account);
        accounts.flush();
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
