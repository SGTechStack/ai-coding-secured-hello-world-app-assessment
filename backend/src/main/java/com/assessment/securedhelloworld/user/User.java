package com.assessment.securedhelloworld.user;

import com.assessment.securedhelloworld.auth.LockoutPolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Clock;
import java.time.Instant;

/**
 * A registered account. The password is always stored as a BCrypt hash
 * ({@code passwordHash}); the plaintext password is never persisted or
 * logged anywhere in this entity's lifecycle. {@code forcePasswordChange}
 * is set on accounts issued a default or temporary credential (e.g. the
 * bootstrap admin account) and enforced by
 * {@code ForcePasswordChangeFilter} until the holder changes it.
 *
 * <p><strong>Lockout ownership:</strong> {@code failedLoginAttempts} and
 * {@code lockedUntil} should only be mutated through
 * {@link #recordFailedAttempt(LockoutPolicy, Clock)},
 * {@link #recordSuccess(Clock)}, and {@link #clearLockout()} — production
 * code should not call {@code setFailedLoginAttempts}/{@code setLockedUntil}
 * directly. Those setters remain for JPA and for test fixtures that need
 * to arrange a pre-existing lockout/login state directly.</p>
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private boolean forcePasswordChange = false;

    @Column(nullable = false)
    private int failedLoginAttempts = 0;

    @Column
    private Instant lockedUntil;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column
    private Instant lastLoginAt;

    @Column
    private Instant accountExpiresAt;

    protected User() {
        // JPA
    }

    public User(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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

    public boolean isForcePasswordChange() {
        return forcePasswordChange;
    }

    public void setForcePasswordChange(boolean forcePasswordChange) {
        this.forcePasswordChange = forcePasswordChange;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public Instant getAccountExpiresAt() {
        return accountExpiresAt;
    }

    public void setAccountExpiresAt(Instant accountExpiresAt) {
        this.accountExpiresAt = accountExpiresAt;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /**
     * Records a failed login attempt and locks the account once
     * {@code policy.maxAttempts()} consecutive failures are reached.
     * The sole entry point for mutating {@code failedLoginAttempts}/
     * {@code lockedUntil} on a failed attempt — callers should not set
     * those fields directly.
     *
     * @return {@code true} if this attempt just triggered the lockout
     *         (i.e. the threshold was reached on this call), so callers
     *         can log the transition without re-deriving it themselves
     */
    public boolean recordFailedAttempt(LockoutPolicy policy, Clock clock) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= policy.maxAttempts()) {
            lockedUntil = clock.instant().plus(policy.lockoutDuration());
            return true;
        }
        return false;
    }

    /**
     * Records a successful login: clears any lockout state and stamps
     * {@code lastLoginAt}. The sole entry point for that combination —
     * callers should not set {@code failedLoginAttempts}/
     * {@code lockedUntil}/{@code lastLoginAt} directly on success.
     */
    public void recordSuccess(Clock clock) {
        failedLoginAttempts = 0;
        lockedUntil = null;
        lastLoginAt = clock.instant();
    }

    /**
     * Clears lockout state without touching {@code lastLoginAt} —
     * distinct from {@link #recordSuccess(Clock)} because this is used
     * outside the login flow (e.g. a successful password reset), where
     * "logged in just now" would not be an accurate signal.
     */
    public void clearLockout() {
        failedLoginAttempts = 0;
        lockedUntil = null;
    }
}
