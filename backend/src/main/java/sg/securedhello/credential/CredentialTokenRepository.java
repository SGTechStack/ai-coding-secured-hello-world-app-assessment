package sg.securedhello.credential;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Credential tokens (ADR-007). Minting and the conditional consume arrive with the flows that issue tokens. */
public interface CredentialTokenRepository extends JpaRepository<CredentialToken, UUID> {

    /**
     * Invalidates the account's pending tokens of {@code type} by deleting them; a consumed token is kept. Any
     * successful password set invalidates the pending reset tokens (ADR-007).
     *
     * @return how many tokens were invalidated
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM CredentialToken t WHERE t.userId = :userId AND t.type = :type AND t.usedAt IS NULL")
    int deletePending(UUID userId, CredentialTokenType type);
}
