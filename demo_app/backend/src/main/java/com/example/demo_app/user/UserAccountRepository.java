package com.example.demo_app.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Accounts by id and by normalised username (see {@link UserAccount#normaliseUsername}). */
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

  Optional<UserAccount> findByUsername(String username);
}
