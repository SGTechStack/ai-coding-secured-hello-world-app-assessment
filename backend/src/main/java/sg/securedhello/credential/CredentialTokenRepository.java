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

    /** The account a stored token belongs to. */
    @Query("SELECT t.userId FROM CredentialToken t WHERE t.tokenHash = :tokenHash")
    Optional<UUID> findUserIdByTokenHash(String tokenHash);
}
