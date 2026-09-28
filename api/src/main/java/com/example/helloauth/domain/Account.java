package com.example.helloauth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The stored record of someone who can authenticate.
 *
 * <p>The class is called {@code Account} while the table is called {@code users}, and the mismatch is
 * deliberate on both sides: the PRD's data model fixes the table name, and "account" is the better
 * domain word because the thing has a role, an enabled flag and a lockout state rather than being a
 * person.
 *
 * <p>Column types are kept to the portable subset — no H2-specific types or defaults — because
 * the schema has to move to Postgres or MySQL unchanged.
 */
@Entity
@Table(
        name = "users",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_users_username", columnNames = "username"),
            @UniqueConstraint(name = "uk_users_email", columnNames = "email")
        })
public class Account {

    /**
     * A UUID rather than a sequence, so that admin endpoints do not expose a guessable,
     * enumerable identifier. Generated in Java, which keeps the mapping portable.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** The login identifier. Normalised to lower case on the way in, so lookups are exact. */
    @Column(name = "username", nullable = false, length = 64)
    private String username;

    /** Used only for password reset. Also normalised to lower case. */
    @Column(name = "email", nullable = false, length = 254)
    private String email;

    /** BCrypt. Never leaves the server, never appears in a response or a log line. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /**
     * Stored as a string, and pinned to {@code VARCHAR} explicitly.
     *
     * <p>The explicit {@code @JdbcTypeCode} is load-bearing. Left to itself, Hibernate 6 maps an
     * {@code EnumType.STRING} enum to the dialect's <em>native</em> enum type where one exists — an
     * H2 {@code ENUM} here, which also silently ignores the {@code length} below. That would put a
     * dialect-specific DDL type in the schema the PRD requires to stay portable: Postgres would need
     * a separate {@code CREATE TYPE} object for migrations to manage, and adding a third role later
     * would mean {@code ALTER TYPE} on Postgres or {@code ALTER TABLE} on MySQL rather than nothing
     * at all. A varchar behaves identically on H2, Postgres and MySQL.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "role", nullable = false, length = 16)
    private Role role;

    /** Lets an admin suspend access without destroying the record. */
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /**
     * Consecutive failures. Not decremented by the passage of time, so four failures last week plus one
     * today still locks the account; see decision 3 in {@code docs/decisions.md}.
     */
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    /** Null, or the instant the lockout lifts. A past value is not a lockout. */
    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Account() {
        // for JPA
    }

    public Account(String username, String email, String passwordHash, Role role, Instant createdAt) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = true;
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        this.createdAt = createdAt;
    }

    /**
     * Whether logins are currently refused because of failed attempts. The lockout lifts on its
     * own: nothing has to run on a schedule to clear it.
     */
    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public void recordFailedLogin() {
        failedLoginAttempts++;
    }

    public void lockUntil(Instant until) {
        this.lockedUntil = until;
    }

    /**
     * Back to a clean slate: no lockout, no accumulated failures. Called on a successful login,
     * after a password reset, and when an expired lockout is first observed — so a user who has
     * just served a lockout is not one failure away from the next one.
     */
    public void clearFailedLogins() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
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

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
