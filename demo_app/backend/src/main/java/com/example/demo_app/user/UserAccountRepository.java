package com.example.demo_app.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Accounts by id, and by normalised username or email (see {@link UserAccount#normaliseUsername}
 * and {@link UserAccount#normaliseEmail}): pass normalised values only.
 */
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

  Optional<UserAccount> findByUsername(String username);

  boolean existsByUsername(String username);

  boolean existsByEmail(String email);

  boolean existsByRole(Role role);

  /** Every account, oldest first; the id breaks ties so the order is stable. */
  List<UserAccount> findAllByOrderByCreatedAtAscIdAsc();
}
