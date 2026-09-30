package com.example.auth.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A registered user of the auth app.
 *
 * <p>The table is named {@code users} rather than {@code user} because
 * {@code USER} is a reserved word in ANSI SQL / H2. The password is stored as
 * a BCrypt hash (see {@code SecurityConfig}'s {@code PasswordEncoder} bean);
 * {@link #failedLoginAttempts}, {@link #failedLoginWindowStart} and {@link
 * #lockedUntil} back the account-lockout requirement.
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
    private String password;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    /** When the current run of {@link #failedLoginAttempts} started; failures older than the lockout window don't count. */
    @Column(name = "failed_login_window_start")
    private Instant failedLoginWindowStart;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    protected User() {
        // JPA
    }

    public User(String username, String email, String password, String firstName) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.firstName = firstName;
        this.role = Role.USER;
        this.enabled = true;
        this.createdAt = Instant.now();
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFirstName() {
        return firstName;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    public Instant getFailedLoginWindowStart() {
        return failedLoginWindowStart;
    }

    public void setFailedLoginWindowStart(Instant failedLoginWindowStart) {
        this.failedLoginWindowStart = failedLoginWindowStart;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(Instant lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }
}
