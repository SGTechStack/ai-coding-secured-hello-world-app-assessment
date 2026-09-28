package com.assessment.securedhelloworld.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for {@link User}, including the account
 * lifecycle queries used by {@code DormantAccountDisablingJob} and
 * {@code AccessReviewJob}.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    long countByRole(Role role);

    /**
     * Enabled accounts that are dormant as of {@code threshold}: either
     * they last logged in before {@code threshold}, or they have never
     * logged in and were created before {@code threshold}.
     */
    @Query("SELECT u FROM User u WHERE u.enabled = true AND "
            + "((u.lastLoginAt IS NOT NULL AND u.lastLoginAt < :threshold) "
            + "OR (u.lastLoginAt IS NULL AND u.createdAt < :threshold))")
    List<User> findDormantEnabledAccounts(@Param("threshold") Instant threshold);

    /**
     * Enabled accounts whose declared {@code accountExpiresAt} has
     * already passed.
     */
    @Query("SELECT u FROM User u WHERE u.enabled = true AND "
            + "u.accountExpiresAt IS NOT NULL AND u.accountExpiresAt < :now")
    List<User> findExpiredEnabledAccounts(@Param("now") Instant now);

    List<User> findByRole(Role role);
}
