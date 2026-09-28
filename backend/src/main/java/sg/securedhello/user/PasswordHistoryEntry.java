package sg.securedhello.user;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** A prior password hash of one account ({@code password_history}); purged with the account by cascade (REJ-035). */
@Entity
@Table(name = "password_history", indexes =
        @Index(name = "ix_password_history_user_id_created_at", columnList = "user_id, created_at"))
public class PasswordHistoryEntry {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PasswordHistoryEntry() {
    }

    public UUID getId() {
        return id;
    }
}
