package sg.securedhello.mfa;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A provisioned, unconfirmed TOTP secret ({@code pending_totp}). Confirmation copies {@code totp_key} verbatim into
 * {@link TotpUserDetails}, so both hold the same 69-byte envelope (ADR-028; ADR-053).
 */
@Entity
@Table(name = "pending_totp")
public class PendingTotp {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "totp_key", nullable = false, length = 69)
    private byte[] totpKey;

    @Column(name = "key_version", nullable = false)
    private short keyVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PendingTotp() {
    }

    /** A pending enrolment of {@code userId}: its sealed secret and the key version it was sealed under. */
    PendingTotp(UUID userId, byte[] totpKey, int keyVersion, Instant createdAt) {
        this.userId = userId;
        replace(totpKey, keyVersion, createdAt);
    }

    /** Provisioning again replaces the secret; the row stays one per user (ADR-053). */
    void replace(byte[] totpKey, int keyVersion, Instant createdAt) {
        this.totpKey = totpKey.clone();
        this.keyVersion = (short) keyVersion;
        this.createdAt = createdAt;
    }

    public UUID getUserId() {
        return userId;
    }

    byte[] getTotpKey() {
        return totpKey.clone();
    }

    int getKeyVersion() {
        return keyVersion;
    }
}
