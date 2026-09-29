package com.example.securedhello.account;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/** Accounts. Derived queries only; callers pass lowercase usernames and emails. */
interface AccountRepository extends JpaRepository<Account, UUID> {

	boolean existsByUsernameOrEmail(String username, String email);

	boolean existsByUsername(String username);

	boolean existsByEmail(String email);

	/** The Account with this (already lowercase) email, if any: for password-reset issuance. */
	Optional<Account> findByEmail(String email);

	/** Whether any Account holds the role, enabled or not. */
	boolean existsByRole(Role role);

	/** The Account, with its row locked until the transaction ends (the login decision). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Account> findForUpdateByUsername(String username);

	/** The Account, with its row locked until the transaction ends (Password Change). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Account> findForUpdateById(UUID id);

	/**
	 * Every enabled Account holding the role, locked until the transaction ends, in a fixed order
	 * (the last-Admin rule). Two concurrent admin changes that both need this always lock the same
	 * rows in the same order, so they queue instead of deadlocking on each other's target row.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	List<Account> findByRoleAndEnabledTrueOrderById(Role role);

}
