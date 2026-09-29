package com.example.securedhello.account;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Reset Tokens. Derived queries only; tokens are never deleted, only marked used. */
interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

	Optional<PasswordResetToken> findByTokenHash(String tokenHash);

	/** Every not-yet-used token for the Account, pending or expired. */
	List<PasswordResetToken> findByUserIdAndUsedAtIsNull(UUID userId);

}
