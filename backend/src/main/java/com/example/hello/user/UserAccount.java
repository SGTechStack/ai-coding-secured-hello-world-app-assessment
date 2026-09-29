package com.example.hello.user;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserAccount {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 50) private String username;
    @Column(nullable = false, unique = true, length = 254) private String email;
    @Column(nullable = false, length = 100) private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private Role role;
    @Column(nullable = false) private boolean enabled;
    @Column(nullable = false) private int failedLoginAttempts;
    private Instant firstFailedAt;
    private Instant lockedUntil;
    @Column(nullable = false) private long securityVersion;
    @Column(nullable = false) private Instant createdAt;

    protected UserAccount() {}

    public UserAccount(String username, String email, String passwordHash, Role role, Instant now) {
        this.id = UUID.randomUUID();
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = true;
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public boolean isEnabled() { return enabled; }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public Instant getLockedUntil() { return lockedUntil; }
    public long getSecurityVersion() { return securityVersion; }
    public Instant getCreatedAt() { return createdAt; }

    public boolean isLocked(Instant now) { return lockedUntil != null && lockedUntil.isAfter(now); }

    public boolean recordFailure(Instant now) {
        if (firstFailedAt == null || !firstFailedAt.plusSeconds(900).isAfter(now)
                || (lockedUntil != null && !lockedUntil.isAfter(now))) {
            clearFailures();
            firstFailedAt = now;
        }
        failedLoginAttempts++;
        if (failedLoginAttempts >= 5) {
            lockedUntil = now.plusSeconds(900);
            return true;
        }
        return false;
    }

    public void clearFailures() {
        failedLoginAttempts = 0;
        firstFailedAt = null;
        lockedUntil = null;
    }

    public void changePassword(String hash) {
        passwordHash = hash;
        clearFailures();
        securityVersion++;
    }

    public void changeEnabled(boolean enabled) { this.enabled = enabled; securityVersion++; }
    public void changeRole(Role role) { this.role = role; securityVersion++; }
}
