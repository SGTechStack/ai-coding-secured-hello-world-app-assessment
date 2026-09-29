package com.example.securedhello.account;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Accounts. Derived queries only; callers pass lowercase usernames and emails. */
interface AccountRepository extends JpaRepository<Account, UUID> {

	boolean existsByUsernameOrEmail(String username, String email);

}
