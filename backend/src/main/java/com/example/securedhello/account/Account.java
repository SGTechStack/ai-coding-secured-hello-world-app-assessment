package com.example.securedhello.account;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The persisted record of a person who has registered. Username and email are always lowercase.
 */
@Entity
@Table(name = "users")
public class Account {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private String username;

	private String email;

	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	private Role role;

	private boolean enabled;

	private boolean passwordChangeRequired;

	private int failedLoginAttempts;

	private Instant lockedUntil;

	private Instant createdAt;

	protected Account() {
	}

	/** A newly registered, enabled Account with the User role. */
	static Account registered(String username, String email, String passwordHash, Instant now) {
		Account account = new Account();
		account.username = username;
		account.email = email;
		account.passwordHash = passwordHash;
		account.role = Role.USER;
		account.enabled = true;
		account.createdAt = now;
		return account;
	}

	/** The Bootstrap Admin: an enabled Admin that must change its configured password at first login. */
	static Account bootstrapAdmin(String username, String email, String passwordHash, Instant now) {
		Account account = registered(username, email, passwordHash, now);
		account.role = Role.ADMIN;
		account.passwordChangeRequired = true;
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

	public String getPasswordHash() {
		return passwordHash;
	}

	public Role getRole() {
		return role;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public boolean isPasswordChangeRequired() {
		return passwordChangeRequired;
	}

	/** Whether the Account is Locked at the given instant. */
	boolean isLocked(Instant now) {
		return lockedUntil != null && now.isBefore(lockedUntil);
	}

	/**
	 * Counts a wrong password. Once a lock has expired the count starts again; reaching the threshold
	 * makes the Account Locked until {@code now + duration}. Wrong passwords during a lock are counted
	 * but do not extend it.
	 * @return whether this failure made the Account Locked
	 */
	boolean recordFailedLogin(Instant now, int threshold, Duration duration) {
		if (lockedUntil != null && !isLocked(now)) {
			lockedUntil = null;
			failedLoginAttempts = 0;
		}
		failedLoginAttempts++;
		if (lockedUntil == null && failedLoginAttempts >= threshold) {
			lockedUntil = now.plus(duration);
			return true;
		}
		return false;
	}

	/**
	 * Replaces the password with one that has passed the Credential policy and Password History, and
	 * clears any Required Password Change, which choosing a new password is exactly what satisfies.
	 * @return whether a Required Password Change was cleared, for its audit event
	 */
	boolean changePassword(String newPasswordHash) {
		passwordHash = newPasswordHash;
		boolean wasRequired = passwordChangeRequired;
		passwordChangeRequired = false;
		return wasRequired;
	}

	/**
	 * An Admin who suspects the Account is compromised requires the holder to choose a new password
	 * before doing anything else. Idempotent.
	 * @return whether a Required Password Change was already set, for the audit event's before state
	 */
	boolean requirePasswordChange() {
		boolean wasRequired = passwordChangeRequired;
		passwordChangeRequired = true;
		return wasRequired;
	}

	/** An Admin permits or suspends login. Never touches {@code passwordChangeRequired} (ADR 0001). */
	void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	/** An Admin grants or revokes the Admin role. */
	void changeRole(Role role) {
		this.role = role;
	}

	/** A successful login clears the failure count and any expired lock. */
	void recordSuccessfulLogin() {
		failedLoginAttempts = 0;
		lockedUntil = null;
	}

	/**
	 * An Admin lifts a Lock, so the holder can log in at once with the correct password, and a fresh
	 * threshold of wrong passwords is needed to lock the Account again. Safe to call on an Account that
	 * is not currently Locked.
	 */
	void unlock() {
		failedLoginAttempts = 0;
		lockedUntil = null;
	}

}
