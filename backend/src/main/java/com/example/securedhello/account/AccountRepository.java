package com.example.securedhello.account;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/** Accounts. Derived queries only; callers pass lowercase usernames and emails. */
interface AccountRepository extends JpaRepository<Account, UUID> {

	boolean existsByUsernameOrEmail(String username, String email);

	/** The Account, with its row locked until the transaction ends (the login decision). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Account> findForUpdateByUsername(String username);

	/** The Account, with its row locked until the transaction ends (Password Change). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Account> findForUpdateById(UUID id);

}
