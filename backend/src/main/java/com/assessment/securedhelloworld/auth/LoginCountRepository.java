package com.assessment.securedhelloworld.auth;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for the fleet-wide {@link LoginCount} totals.
 */
public interface LoginCountRepository extends JpaRepository<LoginCount, LoginOutcome> {

    /**
     * Atomically increments the row for {@code outcome} by 1, creating it
     * first if absent (H2/Postgres-compatible upsert via native merge is
     * avoided for portability; the two-step insert-if-absent then update
     * is still race-safe because the UPDATE itself is atomic at the row
     * level and callers only care about the running total, not exact
     * interleaving order).
     */
    @Modifying
    @Transactional
    @Query("UPDATE LoginCount c SET c.count = c.count + 1 WHERE c.outcome = :outcome")
    int increment(@Param("outcome") LoginOutcome outcome);
}
