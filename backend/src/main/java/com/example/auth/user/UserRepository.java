package com.example.auth.user;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByRole(Role role);

    /**
     * Loads the User under a pessimistic write lock (SELECT … FOR UPDATE).
     * Must be called inside a @Transactional context. Used by LockoutService
     * so that concurrent failed-login recordings serialise on the row and
     * the lockout threshold can never be bypassed via a lost-update race (Story 45).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.username = :username")
    Optional<User> findByUsernameWithLock(String username);
}
