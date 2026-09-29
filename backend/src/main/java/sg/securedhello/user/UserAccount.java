package sg.securedhello.user;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * An account ({@code users}). Being locked is derived from {@link #lockedUntil} and never stored (REJ-017); MFA
 * enrolment is the existence of a {@code totp_user_details} row, not a flag (ADR-053).
 */
@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "ux_users_username", columnNames = "username"),
        @UniqueConstraint(name = "ux_users_email", columnNames = "email")})
public class UserAccount {

    /** Application-generated UUIDv4, assigned at {@code persist()} (ADR-050). */
    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "username", nullable = false, length = 32)
    private String username;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    /** Null until the user sets a password by redeeming a token (ADR-006; ADR-032). */
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    /** A {@code roles.name}, enforced by foreign key (ADR-042). */
    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "last_failed_at")
    private Instant lastFailedAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "consecutive_failures_since_success", nullable = false)
    private int consecutiveFailuresSinceSuccess;

    @Column(name = "password_disabled_at")
    private Instant passwordDisabledAt;

    @Column(name = "force_password_change", nullable = false)
    private boolean forcePasswordChange;

    @Column(name = "credential_issued_at")
    private Instant credentialIssuedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserAccount() {
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    /** The encoded password, or {@code null} for an account that has never set one. */
    public String getPasswordHash() {
        return passwordHash;
    }

    public String getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** When the account was activated, or {@code null} if it never was. */
    public Instant getActivatedAt() {
        return activatedAt;
    }

    /** The password-lockout columns (ADR-011; ADR-012; ADR-013). */
    public PasswordLockoutState getLockoutState() {
        return new PasswordLockoutState(failedLoginAttempts, lastFailedAt, lockedUntil,
                consecutiveFailuresSinceSuccess, passwordDisabledAt);
    }

    /** Writes the password-lockout columns; the caller holds the row lock. */
    public void setLockoutState(PasswordLockoutState state) {
        this.failedLoginAttempts = state.failedLoginAttempts();
        this.lastFailedAt = state.lastFailedAt();
        this.lockedUntil = state.lockedUntil();
        this.consecutiveFailuresSinceSuccess = state.consecutiveFailuresSinceSuccess();
        this.passwordDisabledAt = state.passwordDisabledAt();
    }
}
