package com.eitri.auth;

import com.eitri.audit.AuditAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** A row in {@code users}: the PRD account model. Username and email are stored lower-case. */
@Entity
@Table(name = "users")
class Account {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "username", nullable = false, unique = true, length = 64)
    private String username;

    @Column(name = "email", nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private Role role;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    /** When the current run of consecutive failures began; null when there is no run. */
    @Column(name = "failed_login_window_started_at")
    private Instant failedLoginWindowStartedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Account() {}

    Account(UUID id, String username, String email, String passwordHash, Role role, Instant createdAt) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = true;
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        this.failedLoginWindowStartedAt = null;
        this.createdAt = createdAt;
    }

    UUID getId() {
        return id;
    }

    String getUsername() {
        return username;
    }

    String getEmail() {
        return email;
    }

    String getPasswordHash() {
        return passwordHash;
    }

    Role getRole() {
        return role;
    }

    boolean isEnabled() {
        return enabled;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    boolean isLockedAt(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /**
     * Counts a failed login. {@code threshold} consecutive failures inside one {@code window} (measured
     * from the first failure of the run) lock the account for {@code lockDuration}. A run whose window
     * has passed, or whose lock has expired, starts again from this failure, so an old run can never
     * make a single new mistake lock the account.
     *
     * @return whether this failure locked the account
     */
    boolean authenticationFailed(Instant now, int threshold, Duration lockDuration, Duration window) {
        if (!enabled || isLockedAt(now)) {
            return false;
        }

        boolean lockExpired = lockedUntil != null;
        boolean windowPassed = failedLoginWindowStartedAt != null
                && !now.isBefore(failedLoginWindowStartedAt.plus(window));
        if (lockExpired || windowPassed) {
            failedLoginAttempts = 0;
            lockedUntil = null;
            failedLoginWindowStartedAt = null;
        }
        // A run recorded before the window was tracked has no start; it counts as current.
        if (failedLoginWindowStartedAt == null) {
            failedLoginWindowStartedAt = now;
        }

        failedLoginAttempts++;
        if (failedLoginAttempts >= threshold) {
            lockedUntil = now.plus(lockDuration);
            return true;
        }
        return false;
    }

    /** The id and username that name this account in audit lines. */
    AuditAccount auditAccount() {
        return new AuditAccount(id, username);
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    void changeRole(Role role) {
        this.role = role;
    }

    void changePasswordHash(String newPasswordHash) {
        passwordHash = newPasswordHash;
    }

    void authenticationSucceeded() {
        failedLoginAttempts = 0;
        lockedUntil = null;
        failedLoginWindowStartedAt = null;
    }
}
