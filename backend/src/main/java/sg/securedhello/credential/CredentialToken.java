package sg.securedhello.credential;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * An activation or password-reset token ({@code credential_tokens}, ADR-007). It stores only the domain-separated
 * hash, never the token.
 */
@Entity
@Table(name = "credential_tokens",
        uniqueConstraints = @UniqueConstraint(name = "ux_credential_tokens_token_hash", columnNames = "token_hash"),
        indexes = @Index(name = "ix_credential_tokens_user_id_type", columnList = "user_id, type"))
public class CredentialToken {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** A plain string column behind a check constraint, not a database enum type. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "type", nullable = false, length = 20)
    private CredentialTokenType type;

    /** Lowercase hex SHA-256 of {@code type_label + ":" + token}. */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CredentialToken() {
    }

    public UUID getId() {
        return id;
    }
}
