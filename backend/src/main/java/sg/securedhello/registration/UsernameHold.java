package sg.securedhello.registration;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A registration's hold on its username ({@code username_holds}) for the pending period, taken whatever state the
 * email address is in (ADR-032 amendment of 2026-09-29). While it lasts, the username is unavailable to any other
 * address, so two registrations of one username answer alike whether the first address was new, pending, activated,
 * invited or tombstoned.
 */
@Entity
@Table(name = "username_holds",
        uniqueConstraints = @UniqueConstraint(name = "ux_username_holds_username", columnNames = "username"),
        indexes = @Index(name = "ix_username_holds_expires_at", columnList = "expires_at"))
class UsernameHold {

    /** Application-generated UUIDv4 (ADR-050). */
    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "username", nullable = false, length = 32)
    private String username;

    /** The canonical address the hold was taken for (ADR-045). */
    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected UsernameHold() {
    }

    UsernameHold(String username, String email, Instant expiresAt) {
        this.username = username;
        this.email = email;
        this.expiresAt = expiresAt;
    }

    String getEmail() {
        return email;
    }

    /** Whether the hold still blocks the username at {@code now}: it runs out at its expiry. */
    boolean liveAt(Instant now) {
        return now.isBefore(expiresAt);
    }

    /** Holds the username for {@code email} until {@code expiresAt}. */
    void renew(String email, Instant expiresAt) {
        this.email = email;
        this.expiresAt = expiresAt;
    }
}
