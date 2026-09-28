package com.example.auth.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository over the seeded {@code users} table. Deliberately not mocked in
 * the backend HTTP-seam tests (Seam 1) -- login/me/logout are exercised
 * against a real H2 database.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmail(String email);

    boolean existsByRole(Role role);
}
