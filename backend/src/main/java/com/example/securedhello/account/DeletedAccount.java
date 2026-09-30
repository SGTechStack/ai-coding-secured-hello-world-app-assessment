package com.example.securedhello.account;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The tombstone of a Deleted Account: the soft-delete record kept indefinitely, so the audit trail
 * survives the Account's removal (ADR 0001). It keeps the deleted Account's own UUID as its id, its
 * username and email, when it was deleted and which Admin deleted it. The username is unique, so it
 * can never be registered or bootstrapped again; the email is not, because a Deleted Account's email
 * may be reused by a new Account.
 */
@Entity
@Table(name = "deleted_users")
class DeletedAccount {

	/** The deleted Account's own UUID, never a new one: the tombstone stands for that Account. */
	@Id
	private UUID id;

	private String username;

	private String email;

	private Instant deletedAt;

	/** The deleting Admin's UUID. No foreign key: their own Account may be deleted later. */
	private UUID deletedBy;

	protected DeletedAccount() {
	}

	DeletedAccount(Account deleted, UUID deletingAdminId, Instant deletedAt) {
		this.id = deleted.getId();
		this.username = deleted.getUsername();
		this.email = deleted.getEmail();
		this.deletedAt = deletedAt;
		this.deletedBy = deletingAdminId;
	}

}
