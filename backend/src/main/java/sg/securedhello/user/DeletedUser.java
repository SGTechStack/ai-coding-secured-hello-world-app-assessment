package sg.securedhello.user;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A deleted-user tombstone ({@code deleted_users}), kept indefinitely; it blocks reuse of both identifiers (ADR-044).
 * Neither id is a foreign key: the tombstone must outlive the deleted row and the deleting admin (REJ-034).
 */
@Entity
@Table(name = "deleted_users", uniqueConstraints = {
        @UniqueConstraint(name = "ux_deleted_users_username", columnNames = "username"),
        @UniqueConstraint(name = "ux_deleted_users_email_hmac", columnNames = "email_hmac")})
public class DeletedUser {

    /** The deleted account's own id, reused rather than generated (ADR-050). */
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "username", nullable = false, length = 32)
    private String username;

    /** Lowercase hex HMAC-SHA-256 of the canonical email (ADR-044; ADR-052). */
    @Column(name = "email_hmac", nullable = false, length = 64)
    private String emailHmac;

    @Column(name = "deleted_at", nullable = false)
    private Instant deletedAt;

    @Column(name = "deleted_by_id", nullable = false)
    private UUID deletedById;

    protected DeletedUser() {
    }

    DeletedUser(UUID userId, String username, String emailHmac, Instant deletedAt, UUID deletedById) {
        this.userId = userId;
        this.username = username;
        this.emailHmac = emailHmac;
        this.deletedAt = deletedAt;
        this.deletedById = deletedById;
    }

    public UUID getUserId() {
        return userId;
    }
}
