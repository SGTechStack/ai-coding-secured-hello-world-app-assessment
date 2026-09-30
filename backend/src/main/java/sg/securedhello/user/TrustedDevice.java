package sg.securedhello.user;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * A device a correct password was entered on ({@code trusted_devices}; ADR-075). Its id is what the device cookie
 * carries, under an HMAC; this row binds it to one account until it expires or is revoked, and holds the device lane's
 * lock. Rows are written under the account's row lock, like the account's own lockout columns (R-DATA-014).
 */
@Entity
@Table(name = "trusted_devices", indexes = @Index(name = "ix_trusted_devices_user_id", columnList = "user_id"))
public class TrustedDevice {

    /** Application-generated UUIDv4, assigned at {@code persist()} (ADR-050); unguessable, and signed in the cookie. */
    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "last_failed_at")
    private Instant lastFailedAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    protected TrustedDevice() {
    }

    /** A new trusted device of {@code userId}, trusted from {@code now} until {@code expiresAt}. */
    public TrustedDevice(UUID userId, Instant now, Instant expiresAt) {
        this.userId = userId;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    /** Whether the device is still trusted at {@code now}: its cookie has not expired. */
    public boolean trustedAt(Instant now) {
        return expiresAt.isAfter(now);
    }

    /** The device lane's lockout columns (ADR-075). */
    public DeviceLockState getLockState() {
        return new DeviceLockState(failedLoginAttempts, lastFailedAt, lockedUntil, consecutiveFailures);
    }

    /** Writes the device lane's lockout columns; the caller holds the account's row lock. */
    public void setLockState(DeviceLockState state) {
        this.failedLoginAttempts = state.failedLoginAttempts();
        this.lastFailedAt = state.lastFailedAt();
        this.lockedUntil = state.lockedUntil();
        this.consecutiveFailures = state.consecutiveFailures();
    }
}
