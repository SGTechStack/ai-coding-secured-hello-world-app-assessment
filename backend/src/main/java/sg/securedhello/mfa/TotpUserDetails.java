package sg.securedhello.mfa;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A confirmed TOTP factor ({@code totp_user_details}). The row's existence is the enrolment (ADR-053); its key is the
 * user's id. {@code totp_key} is the 69-byte envelope, pinned by named checks the validator cannot see (ADR-028).
 *
 * <p>It owns the two-tier lockout (ADR-027), counted under the row lock by {@link TotpVerification}:
 * <ul>
 *   <li><b>Tier 1:</b> {@value #LOCK_THRESHOLD} consecutive failures, each less than {@link #WINDOW} after the one
 *       before (the password axis's windowed counter, ADR-012), lock the factor for {@link #LOCK}. The lock lifts by
 *       itself, and the first failure after it restarts the count, since the lock is as long as the window. A success
 *       clears the counter and the lock.</li>
 *   <li><b>Tier 2:</b> the {@value #DISABLE_THRESHOLD}th cumulative failure disables the factor, which outranks a
 *       lock. A success never resets that count; only rebinding, which deletes the row, clears it.</li>
 * </ul>
 * A failure that finds the factor locked or disabled is never counted: the verification refuses it first.
 */
@Entity
@Table(name = "totp_user_details")
public class TotpUserDetails {

    /** Tier 1: failures inside the window that lock the factor. */
    static final int LOCK_THRESHOLD = 10;

    /** Tier 1: the observation window, the most a failure may follow the one before and still count on. */
    static final Duration WINDOW = Duration.ofMinutes(20);

    /** Tier 1: how long a lock lasts. */
    static final Duration LOCK = Duration.ofMinutes(20);

    /** Tier 2: the cumulative failures that disable the factor (NIST SP 800-63B-4 §3.2.2's upper bound). */
    static final int DISABLE_THRESHOLD = 100;

    /** What a counted failure did. */
    enum Failure {
        /** Counted, below both thresholds. */
        COUNTED,
        /** Counted, and tier 1 locked the factor. */
        LOCKED,
        /** Counted, and tier 2 disabled the factor. */
        DISABLED
    }

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

    /**
     * Records {@code counter} as accepted, so it and every earlier counter are replays from now on (R-MFA-017), and
     * clears tier 1. The cumulative count stays (ADR-027).
     */
    void accept(long counter) {
        lastUsedCounter = counter;
        failedAttempts = 0;
        lastFailedAt = null;
        lockedUntil = null;
    }

    /** The end of the tier-1 lock, if the factor is locked at {@code now}. */
    Optional<Instant> lockedUntil(Instant now) {
        return Optional.ofNullable(lockedUntil).filter(until -> until.isAfter(now));
    }

    /** Counts a wrong code at {@code now} on both tiers; the caller found the factor neither locked nor disabled. */
    Failure fail(Instant now) {
        failedAttempts = lastFailedAt != null && now.isBefore(lastFailedAt.plus(WINDOW)) ? failedAttempts + 1 : 1;
        lastFailedAt = now;
        cumulativeFailures++;
        if (cumulativeFailures >= DISABLE_THRESHOLD) {
            factorDisabledAt = now;
            return Failure.DISABLED;
        }
        if (failedAttempts >= LOCK_THRESHOLD) {
            lockedUntil = now.plus(LOCK);
            return Failure.LOCKED;
        }
        return Failure.COUNTED;
    }

    /**
     * An admin unlock (REJ-072): clears tier 1, its counter, anchor and lock, and never tier 2, whose cumulative count
     * and disable only rebinding clears (ADR-027). Only {@code AdminActions} calls it, under its lock set (ArchUnit).
     */
    public void unlockTier1() {
        failedAttempts = 0;
        lastFailedAt = null;
        lockedUntil = null;
    }

    /** Whether tier 2 has disabled the factor, so only rebinding restores it (ADR-027). */
    boolean isDisabled() {
        return factorDisabledAt != null;
    }
}
