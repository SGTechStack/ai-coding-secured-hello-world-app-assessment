package com.assessment.securedhelloworld.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

/**
 * Schema uses only standard JPA/SQL types (no H2-specific dialect features) so it is
 * portable to Postgres/MySQL later via a dialect/config change alone (PRD Overview).
 * Implements {@link Serializable}: it rides inside {@code AppUserDetails} in the
 * Spring-Session-JDBC-backed HTTP session, which persists attributes via Java serialization.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /** Set on admin-issued (bootstrap-seeded) credentials; cleared once the user changes their own password. */
    @Column(name = "force_password_change", nullable = false)
    private boolean forcePasswordChange;

    public User(String username, String email, String passwordHash, Role role) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = true;
        this.failedLoginAttempts = 0;
        this.createdAt = Instant.now();
        this.forcePasswordChange = false;
    }

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }
}
