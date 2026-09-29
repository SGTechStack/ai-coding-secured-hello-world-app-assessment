package sg.securedhello.credential;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mints and redeems credential tokens (ADR-007). The plaintext exists only in the return value of {@link #mint}; the
 * table holds its domain-separated hash ({@link CredentialTokenHash}).
 */
@Service
public class CredentialTokens {

    private final CredentialTokenRepository tokens;
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    CredentialTokens(CredentialTokenRepository tokens, Clock clock) {
        this.tokens = tokens;
        this.clock = clock;
    }

    /**
     * Issues a {@code type} token for {@code userId}, in the caller's transaction, cancelling the account's earlier
     * unredeemed tokens of that type first.
     *
     * @return the plaintext token, to deliver once and never store
     */
    @Transactional
    public String mint(UUID userId, CredentialTokenType type) {
        tokens.deletePending(userId, type);
        String token = CredentialTokenHash.generate(random);
        tokens.save(new CredentialToken(userId, type, CredentialTokenHash.hash(type, token), now()));
        return token;
    }

    /**
     * Consumes a submitted {@code type} token with the single conditional update, in the caller's transaction, so a
     * later failure in that transaction (a rejected password) rolls the consume back and leaves the token redeemable.
     *
     * @return the account the token belonged to, or empty if it is unknown, of another type, used or expired
     */
    @Transactional
    public Optional<UUID> redeem(CredentialTokenType type, String submitted) {
        return CredentialTokenConsumption.lookup(type, submitted)
                .filter(hash -> CredentialTokenConsumption.redeemed(tokens.consume(hash, type, now())))
                .flatMap(tokens::findUserIdByTokenHash);
    }

    /** The columns are TIMESTAMP(6): work on the stored precision, so an expiry compares what was written. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
