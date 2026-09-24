package com.sgtechstack.helloworldauthapp.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRole(Role role);

    /**
     * Whether a usable admin exists — the question {@code existsByRole} was
     * being asked to answer and could not.
     *
     * <p>A disabled {@code ADMIN} row satisfies {@code existsByRole} while
     * being unable to log in, so the bootstrap runner treated a system with no
     * reachable administrator as already bootstrapped and did nothing. The
     * recovery path was editing the database by hand, which is exactly what
     * seeding exists to avoid.
     */
    boolean existsByRoleAndEnabledTrue(Role role);

    /**
     * How many admins can currently sign in. Used to refuse any mutation that
     * would leave zero, so the system cannot be locked out of its own
     * administration through the supported UI.
     */
    long countByRoleAndEnabledTrue(Role role);

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    List<User> findAllByOrderByCreatedAtAsc();
}
