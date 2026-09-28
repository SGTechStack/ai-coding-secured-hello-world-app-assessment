package com.example.helloauth.repository;

import com.example.helloauth.domain.Account;
import com.example.helloauth.domain.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    /** Usernames are stored already normalised, so this is an exact match by design. */
    Optional<Account> findByUsername(String username);

    Optional<Account> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /** Drives the idempotency of the admin seed: if any admin exists, do not seed another. */
    boolean existsByRole(Role role);

    List<Account> findAllByOrderByCreatedAtAsc();
}
