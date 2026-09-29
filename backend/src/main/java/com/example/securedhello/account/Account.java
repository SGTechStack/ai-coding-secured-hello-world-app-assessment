package com.example.securedhello.account;

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

	public UUID getId() {
		return id;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

}
