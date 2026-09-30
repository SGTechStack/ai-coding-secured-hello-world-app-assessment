package sg.example.helloauth.passwordreset;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    /** Holds the row until the transaction ends, so two confirms with one token can't both succeed. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetToken> findForUpdateByTokenHash(String tokenHash);

    void deleteByUserIdAndUsedAtIsNull(UUID userId);

    void deleteByUserId(UUID userId);
}
