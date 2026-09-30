package sg.securedhello.user;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Trusted devices, by id or by their account (ADR-075). */
public interface TrustedDeviceRepository extends JpaRepository<TrustedDevice, UUID> {

    /** The account's devices, newest first. */
    List<TrustedDevice> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /** Every device locked at {@code now}, for the admin sign-in status. */
    List<TrustedDevice> findByLockedUntilAfter(Instant now);

    /**
     * Revokes every device of the account, so each of its device cookies is untrusted from now on (ADR-075): on a
     * credential change, an admin disable, reset issuance or delete, and a recovery-runner reset.
     *
     * @return how many devices were revoked
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from TrustedDevice device where device.userId = :userId")
    int revokeAll(@Param("userId") UUID userId);
}
