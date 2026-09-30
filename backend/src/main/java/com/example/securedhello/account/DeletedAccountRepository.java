package com.example.securedhello.account;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Tombstones of Deleted Accounts. Derived queries only; rows are written once and never removed. */
interface DeletedAccountRepository extends JpaRepository<DeletedAccount, UUID> {

	/**
	 * Whether a Deleted Account's tombstone holds this (already lowercase) username, in which case no
	 * new Account may ever take it, by registration or by the Bootstrap Admin initializer.
	 */
	boolean existsByUsername(String username);

}
