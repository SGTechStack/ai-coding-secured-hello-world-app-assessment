package sg.securedhello.mfa;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Confirmed factors, keyed on the user's id. A row's existence is the enrolment (ADR-053). */
public interface TotpUserDetailsRepository extends JpaRepository<TotpUserDetails, UUID> {

    /**
     * The factor, with its row locked for the rest of the transaction, held across load, decrypt, compare and write,
     * so replay rejection is atomic (ADR-027; R-MFA-017).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select factor from TotpUserDetails factor where factor.userId = :userId")
    Optional<TotpUserDetails> findForUpdateByUserId(@Param("userId") UUID userId);
}
