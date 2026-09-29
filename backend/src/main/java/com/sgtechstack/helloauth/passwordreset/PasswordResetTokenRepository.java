package com.sgtechstack.helloauth.passwordreset;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.sgtechstack.helloauth.user.User;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

	Optional<PasswordResetToken> findByTokenHash(String tokenHash);

	/** Row lock so two concurrent redemptions of one token cannot both succeed. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from PasswordResetToken t where t.tokenHash = :tokenHash")
	Optional<PasswordResetToken> findByTokenHashForUpdate(String tokenHash);

	@Modifying
	@Query("delete from PasswordResetToken t where t.user = :user and t.usedAt is null")
	int deleteUnusedByUser(User user);

	@Modifying
	@Query("delete from PasswordResetToken t where t.user = :user")
	int deleteAllByUser(User user);

	@Modifying
	@Query("delete from PasswordResetToken t where t.expiresAt < :cutoff")
	int deleteExpiredBefore(Instant cutoff);

}
