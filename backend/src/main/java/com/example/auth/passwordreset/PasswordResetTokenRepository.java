package com.example.auth.passwordreset;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** Marks every still-unused token of a user as used: only the newest link may ever work. */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PasswordResetToken t SET t.usedAt = :usedAt WHERE t.user.id = :userId AND t.usedAt IS NULL")
    int invalidateUnusedTokens(@Param("userId") Long userId, @Param("usedAt") Instant usedAt);

    long countByUserIdAndUsedAtIsNull(Long userId);

    /**
     * Atomic claim of a single-use token: the row flips from unused to used
     * only if it is still unused at the moment of this UPDATE, closing the
     * gap between the {@code isUsable()} read in {@code confirmReset} and a
     * plain {@code save()} -- without this, two concurrent requests racing
     * the same raw token could both pass the read-side check before either
     * commits. Returns the number of rows updated: 0 means someone else
     * already claimed it first.
     *
     * <p>{@code clearAutomatically = true}: a bulk JPQL UPDATE like this runs
     * straight against the DB and bypasses Hibernate's first-level cache, so
     * without clearing the persistence context, the already-loaded {@code
     * PasswordResetToken} entity in {@code confirmReset} (and anything else
     * read later in the same transaction) would keep seeing the stale
     * pre-update {@code usedAt}.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PasswordResetToken t SET t.usedAt = :usedAt WHERE t.id = :id AND t.usedAt IS NULL")
    int markUsed(@Param("id") Long id, @Param("usedAt") Instant usedAt);
}
