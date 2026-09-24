package com.sgtechstack.helloworldauthapp.passwordreset;

import com.sgtechstack.helloworldauthapp.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    void deleteAllByUser(User user);

    /**
     * Every token for this user that has not been consumed yet — the set that
     * has to be invalidated when a new one is issued, so only the newest link
     * ever works.
     *
     * <p>Fetched and marked in a loop rather than updated in bulk with a
     * {@code @Query}. Every repository method in this application is a derived
     * query, which is what makes "SQL injection is not applicable" a structural
     * claim rather than an audit of query strings; a single hand-written
     * statement would cost that. The set is bounded by the reset rate limit
     * (five per hour per caller), so the loop is small by construction.
     */
    List<PasswordResetToken> findAllByUserAndUsedAtIsNull(User user);

    /**
     * Every token row for this user, for the self-service data export. The
     * export reports the request history — when a reset was asked for and
     * whether the link was followed — and never the hash.
     */
    List<PasswordResetToken> findAllByUserOrderByExpiresAtAsc(User user);

    /**
     * Rows whose expiry has passed, consumed or not.
     *
     * <p>Split from {@link #deleteAllByUsedAtBefore} because "expired" and
     * "used" are independent: a token used five minutes after issue is still
     * within its expiry window and would never be caught by an expiry-based
     * sweep alone. Two derived queries rather than one {@code @Query} with an
     * {@code OR}, for the reason above.
     */
    long deleteAllByExpiresAtBefore(Instant cutoff);

    /** Rows consumed before the cutoff, regardless of when they would expire. */
    long deleteAllByUsedAtBefore(Instant cutoff);
}
