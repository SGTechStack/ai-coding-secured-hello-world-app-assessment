package com.example.securedhello.account;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface PasswordHistoryRepository extends JpaRepository<PasswordHistoryEntry, UUID> {

	/** An Account's Password History, newest (the current password) first. */
	List<PasswordHistoryEntry> findByUserIdOrderByCreatedAtDesc(UUID userId);

	/** Removes an Account's whole Password History, when the Account itself is deleted. */
	void deleteByUserId(UUID userId);

}
