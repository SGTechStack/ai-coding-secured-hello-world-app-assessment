package com.sgtechstack.helloworldauthapp.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A registered account. The password is always stored as a BCrypt hash,
 * never plaintext, and never logged.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    /**
     * When the most recent failed login happened, so the failure counter can
     * decay. Without this the counter is a lifetime tally and five failures
     * years apart still lock the account.
     */
    @Column(name = "last_failed_login_at")
    private Instant lastFailedLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Count of successful authentications, incremented by
     * {@code LoginSuccessHandler} on every login. Admin-facing only (see
     * {@code UserSummaryResponse}); nothing in access control reads it.
     */
    @Column(name = "successful_login_count", nullable = false)
    private int successfulLoginCount;

    /**
     * When this account last logged in successfully, set by
     * {@code LoginSuccessHandler}. Null for an account that has never
     * logged in. Admin-facing only, same as {@link #successfulLoginCount};
     * nothing in access control or throttling reads it.
     */
    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected User() {
        // required by JPA
    }

    public User(String username, String email, String passwordHash, Role role, boolean enabled) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = enabled;
        this.failedLoginAttempts = 0;
        this.successfulLoginCount = 0;
        this.createdAt = Instant.now();
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

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(Instant lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public Instant getLastFailedLoginAt() {
        return lastFailedLoginAt;
    }

    public void setLastFailedLoginAt(Instant lastFailedLoginAt) {
        this.lastFailedLoginAt = lastFailedLoginAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int getSuccessfulLoginCount() {
        return successfulLoginCount;
    }

    public void setSuccessfulLoginCount(int successfulLoginCount) {
        this.successfulLoginCount = successfulLoginCount;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }
}
