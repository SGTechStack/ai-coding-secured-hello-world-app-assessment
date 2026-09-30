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
     * unredeemed self-issued tokens of that type first. An administrator's pending token is never cancelled here.
     *
     * @return the plaintext token, to deliver once and never store
     */
    @Transactional
    public String mint(UUID userId, CredentialTokenType type) {
        return issue(userId, type, false);
    }

    /**
     * Issues a {@code type} token for {@code userId} on an administrator's behalf, exactly as {@link #mint} does, but
     * marked admin-issued: an invite's activation token or an admin reset (ADR-006). A self-service request never
     * replaces a pending one ({@link #adminIssuedPending}), and a pending account holding one is an invite, which a
     * self-registration never replaces ({@link #selfRegistered}).
     *
     * @return the plaintext token, to return to the administrator once and never store
     */
    @Transactional
    public String issueForAdmin(UUID userId, CredentialTokenType type) {
        return issue(userId, type, true);
    }

    /**
     * Whether an administrator's {@code type} token for {@code userId} is still pending: unused and unexpired. While
     * one is, a self-service reset request leaves it in place and mints nothing (ADR-007 amendment).
     */
    public boolean adminIssuedPending(UUID userId, CredentialTokenType type) {
        return tokens.existsAdminIssuedPending(userId, type, now());
    }

    /**
     * Whether {@code userId} holds a self-issued activation token, used, expired or pending: its pending registration
     * came from self-registration. An invite holds an admin-issued one instead. An account with neither, such as a
     * disabled one whose tokens were cancelled, is not treated as self-registered, so nothing replaces it.
     */
    public boolean selfRegistered(UUID userId) {
        return tokens.existsByUserIdAndTypeAndAdminIssued(userId, CredentialTokenType.ACTIVATION, false);
    }

    /** Cancels every pending token of {@code userId}, of both types: what an admin disable does (ADR-007). */
    @Transactional
    public void cancelAll(UUID userId) {
        for (CredentialTokenType type : CredentialTokenType.values()) {
            tokens.deletePending(userId, type);
        }
    }

    private String issue(UUID userId, CredentialTokenType type, boolean adminIssued) {
        if (adminIssued) {
            tokens.deletePending(userId, type);
        } else {
            tokens.deletePendingSelfIssued(userId, type);
        }
        String token = CredentialTokenHash.generate(random);
        tokens.save(new CredentialToken(userId, type, CredentialTokenHash.hash(type, token), now(), adminIssued));
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

    /**
     * The account a submitted {@code type} token names, consuming nothing and checking neither use nor expiry, so a
     * caller can lock that account before it {@linkplain #redeem redeems} the token.
     *
     * @return the account, or empty if no token of that type has that hash
     */
    public Optional<UUID> holder(CredentialTokenType type, String submitted) {
        return CredentialTokenConsumption.lookup(type, submitted).flatMap(tokens::findUserIdByTokenHash);
    }

    /** The columns are TIMESTAMP(6): work on the stored precision, so an expiry compares what was written. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
