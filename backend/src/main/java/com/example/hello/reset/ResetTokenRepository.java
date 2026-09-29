package com.example.hello.reset;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;

public interface ResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
  @Query("select t.userId from PasswordResetToken t where t.tokenHash = :hash")
  Optional<UUID> findUserIdByTokenHash(String hash);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from PasswordResetToken t where t.tokenHash = :hash")
  Optional<PasswordResetToken> lockByHash(String hash);

  @Modifying(flushAutomatically = true)
  @Query(
      "update PasswordResetToken t set t.usedAt = :now where t.userId = :userId and t.usedAt is null")
  void consumeAll(UUID userId, Instant now);
}
