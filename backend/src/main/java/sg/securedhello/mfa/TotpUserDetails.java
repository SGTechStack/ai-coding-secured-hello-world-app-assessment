package sg.securedhello.mfa;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A confirmed TOTP factor ({@code totp_user_details}). The row's existence is the enrolment (ADR-053); its key is the
 * user's id. {@code totp_key} is the 69-byte envelope, pinned by named checks the validator cannot see (ADR-028).
 */
@Entity
@Table(name = "totp_user_details")
public class TotpUserDetails {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "totp_key", nullable = false, length = 69)
    private byte[] totpKey;

    @Column(name = "key_version", nullable = false)
    private short keyVersion;

    /** The last accepted time-step counter, for replay rejection. */
    @Column(name = "last_used_counter")
    private Long lastUsedCounter;

    /** Tier 1 (ADR-027). */
    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "last_failed_at")
    private Instant lastFailedAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    /** Tier 2: never reset by a success (ADR-027). */
    @Column(name = "cumulative_failures", nullable = false)
    private int cumulativeFailures;

    @Column(name = "factor_disabled_at")
    private Instant factorDisabledAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TotpUserDetails() {
    }

    /**
     * The factor that <em>enrolment binding</em> creates from {@code pending}: its envelope and key version copied
     * verbatim, with no re-encryption (ADR-028), and the confirming code's counter as the replay floor.
     */
    static TotpUserDetails boundFrom(PendingTotp pending, long confirmedCounter, Instant createdAt) {
        TotpUserDetails factor = new TotpUserDetails();
        factor.userId = pending.getUserId();
        factor.totpKey = pending.getTotpKey();
        factor.keyVersion = (short) pending.getKeyVersion();
        factor.lastUsedCounter = confirmedCounter;
        factor.createdAt = createdAt;
        return factor;
    }

    public UUID getUserId() {
        return userId;
    }

    /** The 69-byte envelope (ADR-028). */
    byte[] getTotpKey() {
        return totpKey;
    }

    int getKeyVersion() {
        return keyVersion;
    }

    /** The last accepted time-step counter, or {@link TotpWindow#NEVER_USED} before the first. */
    long lastUsedCounter() {
        return lastUsedCounter == null ? TotpWindow.NEVER_USED : lastUsedCounter;
    }

    /** Records {@code counter} as accepted, so it and every earlier counter are replays from now on (R-MFA-017). */
    void accept(long counter) {
        lastUsedCounter = counter;
    }

    /** Whether tier 2 has disabled the factor, so only rebinding restores it (ADR-027). */
    boolean isDisabled() {
        return factorDisabledAt != null;
    }
}
