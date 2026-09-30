package sg.example.helloauth.passwordreset;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A Password reset token as stored: only its hash, so a database leak exposes no usable token. */
@Entity
@Table(name = "password_reset_tokens")
class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private UUID userId;

    private String tokenHash;

    private Instant expiresAt;

    private Instant usedAt;

    private Instant createdAt;

    protected PasswordResetToken() {
    }

    static PasswordResetToken issue(UUID userId, String tokenHash, Instant now, Duration validity) {
        PasswordResetToken token = new PasswordResetToken();
        token.userId = userId;
        token.tokenHash = tokenHash;
        token.createdAt = now;
        token.expiresAt = now.plus(validity);
        return token;
    }

    /** Unused and not yet expired: it expires at {@code expiresAt} exactly. */
    boolean isRedeemableAt(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    void markUsed(Instant now) {
        usedAt = now;
    }

    UUID userId() {
        return userId;
    }
}
