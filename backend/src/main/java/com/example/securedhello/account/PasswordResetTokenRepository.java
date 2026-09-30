package com.example.securedhello.account;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reset Tokens. Derived queries only. A token is never deleted while its Account exists, only marked
 * used; deleting the Account removes its tokens with it (spec Schema).
 */
interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

	Optional<PasswordResetToken> findByTokenHash(String tokenHash);

	/** Every not-yet-used token for the Account, pending or expired. */
	List<PasswordResetToken> findByUserIdAndUsedAtIsNull(UUID userId);

	/** Removes every token of an Account, used or not, when the Account itself is deleted. */
	void deleteByUserId(UUID userId);

}
