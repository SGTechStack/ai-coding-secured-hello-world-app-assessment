package com.example.auth.user;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository over the seeded {@code users} table. Deliberately not mocked in
 * the backend HTTP-seam tests (Seam 1) -- login/me/logout are exercised
 * against a real H2 database.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    /**
     * Row-locked read for the lockout counter: two concurrent failed logins against one account
     * would otherwise both read the same {@code failed_login_attempts} and each write back the
     * same increment, letting an attacker get more guesses than the lockout threshold allows.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.username = :username")
    Optional<User> findByUsernameForUpdate(@Param("username") String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmail(String email);

    boolean existsByRole(Role role);

    /**
     * Row-locks every enabled user with {@code role}, serialising concurrent admin changes that
     * could otherwise each see "another admin remains" and together remove the last one.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.role = :role AND u.enabled = true")
    List<User> findEnabledByRoleForUpdate(@Param("role") Role role);
}
