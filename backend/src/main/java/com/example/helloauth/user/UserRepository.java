package com.example.helloauth.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /** The admin seeder's idempotence check — seed only when no ADMIN row exists. */
    boolean existsByRole(Role role);
}
