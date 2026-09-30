package com.assessment.auth.password;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

  Optional<PasswordResetToken> findByTokenHash(String tokenHash);

  /**
   * Issuing a new token deletes any prior unused row (Std:112), which is what makes {@code usedAt}
   * mean exactly "redeemed" and guarantees only the most recently issued token is ever valid.
   */
  void deleteByUserIdAndUsedAtIsNull(UUID userId);

  void deleteByUserId(UUID userId);
}
