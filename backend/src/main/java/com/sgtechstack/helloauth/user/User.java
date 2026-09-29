package com.sgtechstack.helloauth.user;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, unique = true, length = 32)
	private String username;

	@Column(nullable = false, unique = true, length = 254)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 100)
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

	@Column(name = "last_failed_login_at")
	private Instant lastFailedLoginAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected User() {
	}

	public static User create(String username, String email, String passwordHash, Role role, Instant now) {
		User user = new User();
		user.username = username;
		user.email = email;
		user.passwordHash = passwordHash;
		user.role = role;
		user.enabled = true;
		user.createdAt = now;
		return user;
	}

	public boolean isLockedAt(Instant now) {
		return this.lockedUntil != null && now.isBefore(this.lockedUntil);
	}

	/**
	 * Forgets an expired lock, or a failure streak whose last failure is older than the window.
	 */
	public void expireStaleLoginFailures(Instant now, Duration window) {
		if (this.lockedUntil != null) {
			if (!now.isBefore(this.lockedUntil)) {
				clearLoginFailures();
			}
		}
		else if (this.lastFailedLoginAt != null && !now.isBefore(this.lastFailedLoginAt.plus(window))) {
			clearLoginFailures();
		}
	}

	/**
	 * Counts an attempt as failed before the password is checked, so concurrent guesses cannot
	 * exceed the lockout threshold. A successful check calls {@link #clearLoginFailures()}.
	 */
	public void registerLoginAttempt(Instant now) {
		this.failedLoginAttempts++;
		this.lastFailedLoginAt = now;
	}

	public void lockUntil(Instant until) {
		this.lockedUntil = until;
	}

	public void clearLoginFailures() {
		this.failedLoginAttempts = 0;
		this.lockedUntil = null;
		this.lastFailedLoginAt = null;
	}

	public void changePassword(String newPasswordHash) {
		this.passwordHash = newPasswordHash;
	}

	public void changeRole(Role newRole) {
		this.role = newRole;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public UUID getId() {
		return this.id;
	}

	public String getUsername() {
		return this.username;
	}

	public String getEmail() {
		return this.email;
	}

	public String getPasswordHash() {
		return this.passwordHash;
	}

	public Role getRole() {
		return this.role;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public int getFailedLoginAttempts() {
		return this.failedLoginAttempts;
	}

	public Instant getLockedUntil() {
		return this.lockedUntil;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

}
