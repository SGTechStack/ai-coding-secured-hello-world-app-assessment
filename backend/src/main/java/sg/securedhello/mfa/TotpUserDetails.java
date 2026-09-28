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

    public UUID getUserId() {
        return userId;
    }
}
