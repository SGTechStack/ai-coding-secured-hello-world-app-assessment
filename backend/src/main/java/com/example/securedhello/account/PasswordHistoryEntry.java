package com.example.securedhello.account;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One entry of an Account's Password History; the newest entry is the current password. */
@Entity
@Table(name = "password_history")
class PasswordHistoryEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private UUID userId;

	private String passwordHash;

	private Instant createdAt;

	protected PasswordHistoryEntry() {
	}

	PasswordHistoryEntry(UUID userId, String passwordHash, Instant createdAt) {
		this.userId = userId;
		this.passwordHash = passwordHash;
		this.createdAt = createdAt;
	}

	String getPasswordHash() {
		return passwordHash;
	}

}
