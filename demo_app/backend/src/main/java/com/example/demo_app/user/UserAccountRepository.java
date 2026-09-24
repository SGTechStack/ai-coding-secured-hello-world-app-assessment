package com.example.demo_app.user;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/**
 * Accounts by id, and by normalised username or email (see {@link UserAccount#normaliseUsername}
 * and {@link UserAccount#normaliseEmail}): pass normalised values only.
 */
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

  Optional<UserAccount> findByUsername(String username);

  /**
   * The account, row-locked until the transaction ends, so concurrent failed logins can't lose
   * each other's count.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from UserAccount a where a.username = :username")
  Optional<UserAccount> findByUsernameForUpdate(String username);

  boolean existsByUsername(String username);

  boolean existsByEmail(String email);
}
