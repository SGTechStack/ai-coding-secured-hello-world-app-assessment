package com.example.securedhello.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.securedhello.entity.PasswordResetToken;

/**
 * Persistence for {@link PasswordResetToken}. Lookups are by hash (on confirm)
 * and by user (on re-issue and cleanup).
 */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    List<PasswordResetToken> findByUserId(Long userId);

    List<PasswordResetToken> findByUserIdAndUsedFalse(Long userId);
}
