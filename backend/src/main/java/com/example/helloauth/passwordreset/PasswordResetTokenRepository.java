package com.example.helloauth.passwordreset;

import com.example.helloauth.user.User;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /** Lookup by the SHA-256 hash — the plaintext token never touches the query. */
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Admin account deletion (ticket 13): the {@code user_id} FK requires
     * outstanding tokens to go before the user row can be removed.
     */
    void deleteByUser(User user);

    /**
     * Atomic consume (ticket 14 reviewer decision): claims the token iff it is
     * still unused — a concurrent {@code confirm} of the same token gets 0
     * rows back and is rejected, closing the check-then-spend TOCTOU that a
     * read-modify-write would leave open.
     */
    @Modifying
    @Query("update PasswordResetToken t set t.usedAt = :now"
        + " where t.tokenHash = :tokenHash and t.usedAt is null")
    int consumeIfUnused(@Param("tokenHash") String tokenHash,
            @Param("now") Instant now);

    /**
     * One live token per account (ticket 14 reviewer decision): stamps
     * {@code used_at} on every outstanding token the user holds. Runs when a
     * new link is requested (superseded links die) and again on confirm (a
     * concurrently minted second link dies with the consumed one).
     */
    @Modifying
    @Query("update PasswordResetToken t set t.usedAt = :now"
        + " where t.user = :user and t.usedAt is null")
    int invalidateOutstandingForUser(@Param("user") User user,
            @Param("now") Instant now);

    /**
     * Table hygiene (ticket 14 reviewer decision): the unauthenticated
     * {@code /request} endpoint grows the table, so dead rows — consumed or
     * past expiry — are deleted. Live unused tokens stay.
     */
    @Modifying
    @Query("delete from PasswordResetToken t"
        + " where t.usedAt is not null or t.expiresAt < :now")
    int deleteDeadTokens(@Param("now") Instant now);
}
