package com.example.demo_app.passwordreset;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Reset tokens by their hash; the plaintext token is never stored or looked up. The bulk updates
 * don't refresh token entities already loaded in the persistence context: don't read {@code
 * usedAt} from one after calling them.
 */
interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

  Optional<PasswordResetToken> findByTokenHash(String tokenHash);

  /** Marks every unused token of the account used at {@code now}, so only a newer one can work. */
  @Modifying(flushAutomatically = true)
  @Query(
      "update PasswordResetToken t set t.usedAt = :now"
          + " where t.user.id = :userId and t.usedAt is null")
  int invalidateUnusedTokens(@Param("userId") Long userId, @Param("now") Instant now);

  /**
   * Marks one token used at {@code now} if nobody has yet; returns {@code 0} when another request
   * got there first. The check and the write are one statement, so two concurrent confirms of the
   * same link can't both succeed.
   */
  @Modifying(flushAutomatically = true)
  @Query("update PasswordResetToken t set t.usedAt = :now where t.id = :id and t.usedAt is null")
  int markUsed(@Param("id") Long id, @Param("now") Instant now);
}
