package sg.securedhello.credential;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Credential tokens (ADR-007). {@link CredentialTokens} mints and redeems them; this is only their storage. */
public interface CredentialTokenRepository extends JpaRepository<CredentialToken, UUID> {

    /**
     * Invalidates the account's pending tokens of {@code type} by deleting them; a consumed token is kept. Any
     * successful password set invalidates the pending reset tokens, and a new issuance its type's (ADR-007).
     *
     * @return how many tokens were invalidated
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM CredentialToken t WHERE t.userId = :userId AND t.type = :type AND t.usedAt IS NULL")
    int deletePending(UUID userId, CredentialTokenType type);

    /**
     * The single conditional update that consumes a token (ADR-007): it marks the token used only if it is of
     * {@code type}, unused and unexpired at {@code now}, atomically in one statement, so two concurrent redemptions
     * cannot both change the row.
     *
     * @return how many rows changed: one when this call redeemed the token, otherwise none
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE CredentialToken t SET t.usedAt = :now WHERE t.tokenHash = :tokenHash AND t.type = :type"
            + " AND t.usedAt IS NULL AND t.expiresAt > :now")
    int consume(String tokenHash, CredentialTokenType type, Instant now);

    /**
     * As {@link #deletePending}, but only the self-issued ones: a self-service issuance never removes an administrator's
     * token, even one issued between its check and this delete (ADR-007 amendment).
     *
     * @return how many tokens were invalidated
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM CredentialToken t WHERE t.userId = :userId AND t.type = :type AND t.usedAt IS NULL"
            + " AND t.adminIssued = FALSE")
    int deletePendingSelfIssued(UUID userId, CredentialTokenType type);

    /**
     * Expires the account's unused administrator's tokens at {@code now} instead of deleting them, so each row keeps
     * marking what an administrator issued: an invite stays an invite after a disable cancels its token (ADR-007).
     *
     * @return how many tokens were invalidated
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE CredentialToken t SET t.expiresAt = :now WHERE t.userId = :userId AND t.usedAt IS NULL"
            + " AND t.adminIssued = TRUE AND t.expiresAt > :now")
    int expireAdminIssuedPending(UUID userId, Instant now);

    /** Whether {@code userId} has an unused administrator's {@code type} token that is unexpired at {@code now}. */
    @Query("SELECT COUNT(t) > 0 FROM CredentialToken t WHERE t.userId = :userId AND t.type = :type"
            + " AND t.adminIssued = TRUE AND t.usedAt IS NULL AND t.expiresAt > :now")
    boolean existsAdminIssuedPending(UUID userId, CredentialTokenType type, Instant now);

    /** Whether {@code userId} has a {@code type} token, in any state, issued by an administrator or not. */
    boolean existsByUserIdAndTypeAndAdminIssued(UUID userId, CredentialTokenType type, boolean adminIssued);

    /** The account a stored token belongs to. */
    @Query("SELECT t.userId FROM CredentialToken t WHERE t.tokenHash = :tokenHash")
    Optional<UUID> findUserIdByTokenHash(String tokenHash);
}
