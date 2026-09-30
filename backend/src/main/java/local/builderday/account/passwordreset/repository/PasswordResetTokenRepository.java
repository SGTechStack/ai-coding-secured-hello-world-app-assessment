package local.builderday.account.passwordreset.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import local.builderday.account.passwordreset.repository.entity.PasswordResetTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, UUID> {
  Optional<PasswordResetTokenEntity> findByTokenHash(String tokenHash);

  /** Removes every earlier token of the account, so only the one issued next can work. */
  void deleteByUserId(UUID userId);

  // JPQL rather than Criteria: spending a token must be one atomic conditional UPDATE, so of two concurrent confirms
  // with the same token exactly one sees 1.

  /** Spends the token if it is still unused and unexpired; 1 when this call spent it, else 0. */
  @Modifying
  @Query("""
      update PasswordResetTokenEntity t set t.usedAt = :now, t.updatedAt = :now
      where t.id = :id and t.usedAt is null and t.expiresAt > :now""")
  int spend(@Param("id") UUID id, @Param("now") Instant now);
}
