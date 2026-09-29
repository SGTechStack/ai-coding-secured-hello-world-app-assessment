package sg.securedhello.user;

import java.time.Duration;
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

    /** How long a forced-change credential works for, from its issue (ADR-046). */
    public static final Duration FORCED_CHANGE_GRACE = Duration.ofDays(30);

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

    /**
     * A pending registration (CONTEXT.md): a {@code USER} account holding a canonical username and email but no
     * password, enabled but not activated, so it cannot sign in until its activation token is redeemed (ADR-032).
     */
    public static UserAccount pendingRegistration(String username, String email, Instant now) {
        UserAccount account = new UserAccount();
        account.username = username;
        account.email = email;
        account.role = "USER";
        account.enabled = true;
        account.createdAt = now;
        return account;
    }

    /**
     * The bootstrap administrator (ADR-047): an enabled, activated {@code ADMIN} account with no password yet. The
     * caller issues its forced-change credential through {@code PasswordService} in the same transaction.
     */
    public static UserAccount administrator(String username, String email, Instant now) {
        UserAccount account = pendingRegistration(username, email, now);
        account.role = "ADMIN";
        account.activatedAt = now;
        return account;
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
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

    /** When the account was created, or when its pending registration was last replaced. */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Whether this is a pending registration: never activated, so it has no password yet. */
    public boolean isPending() {
        return activatedAt == null;
    }

    /**
     * A repeated self-registration against this pending record replaces it (ADR-032; R-CRED-010): it takes the newly
     * submitted username. The caller has checked that the username is free.
     */
    public void replacePendingRegistration(String newUsername, Instant now) {
        if (!isPending()) {
            throw new IllegalStateException("Only a pending registration is replaced");
        }
        this.username = newUsername;
        this.createdAt = now;
    }

    /** Marks the account activated, once its activation token has been redeemed and its first password set. */
    public void activate(Instant now) {
        if (!isPending()) {
            throw new IllegalStateException("The account is already activated");
        }
        this.activatedAt = now;
    }

    /** Whether the account holds a credential it must change before anything else (ADR-046). */
    public boolean isForcePasswordChange() {
        return forcePasswordChange;
    }

    /**
     * Whether a forced-change credential is outstanding and was issued more than {@link #FORCED_CHANGE_GRACE} before
     * {@code now}. A null issue time never expires (T-ADM-031).
     */
    public boolean forcedChangeExpiredAt(Instant now) {
        return forcePasswordChange && credentialIssuedAt != null
                && now.isAfter(credentialIssuedAt.plus(FORCED_CHANGE_GRACE));
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

    /**
     * Stores a user-chosen password's hash, which also completes any forced change: the flag and the issue time clear
     * (ADR-046). The one writer of the credential column; only {@code PasswordService} may call it (ArchUnit).
     */
    public void replacePasswordHash(String encoded) {
        this.passwordHash = encoded;
        this.forcePasswordChange = false;
        this.credentialIssuedAt = null;
    }

    /**
     * Stores an issued password's hash as a forced-change credential: the flag is set and the issue time stamped, so
     * the lazy expiry applies (ADR-046). It writes the credential column, so only {@code PasswordService} may call it
     * (ArchUnit).
     */
    public void issueCredential(String encoded, Instant issuedAt) {
        this.passwordHash = encoded;
        this.forcePasswordChange = true;
        this.credentialIssuedAt = issuedAt;
    }
}
