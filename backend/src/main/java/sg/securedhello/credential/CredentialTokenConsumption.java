package sg.securedhello.credential;

import java.time.Instant;
import java.util.Optional;

import sg.securedhello.audit.TokenRedemptionFailureReason;

/**
 * The decisions around the single conditional update that consumes a token (ADR-007), as pure functions: which
 * submitted value is worth looking up, and whether the update redeemed it. The update itself is
 * {@link CredentialTokenRepository#consume}.
 */
public final class CredentialTokenConsumption {

    private CredentialTokenConsumption() {
    }

    /**
     * The stored hash to consume for a submitted {@code type} token, or empty when the value cannot be a minted token
     * (missing or misshapen), which is refused without a query.
     */
    public static Optional<String> lookup(CredentialTokenType type, String submitted) {
        if (submitted == null || !CredentialTokenHash.wellFormed(submitted)) {
            return Optional.empty();
        }
        return Optional.of(CredentialTokenHash.hash(type, submitted));
    }

    /**
     * Whether the conditional update redeemed the token: exactly one row changed. None means unknown, of another
     * type, used or expired; more than one is impossible under the unique index on {@code token_hash}.
     *
     * @throws IllegalStateException if more than one row changed
     */
    public static boolean redeemed(int rowsChanged) {
        if (rowsChanged > 1) {
            throw new IllegalStateException("A token hash matched " + rowsChanged + " rows; it must be unique");
        }
        return rowsChanged == 1;
    }

    /**
     * Why a token did not redeem, from what the store holds for its hash at {@code now}: a used token is
     * {@code TOKEN_CONSUMED}, an unused one past its expiry {@code TOKEN_EXPIRED}. Anything else, no token or a live
     * one that names no account that can redeem it, is {@code TOKEN_UNKNOWN}.
     *
     * @param stored what the store holds for the hash; empty when no token of the type has it
     */
    public static TokenRedemptionFailureReason failure(Optional<CredentialTokenState> stored, Instant now) {
        if (stored.isEmpty()) {
            return TokenRedemptionFailureReason.TOKEN_UNKNOWN;
        }
        if (stored.get().usedAt() != null) {
            return TokenRedemptionFailureReason.TOKEN_CONSUMED;
        }
        return now.isBefore(stored.get().expiresAt()) ? TokenRedemptionFailureReason.TOKEN_UNKNOWN
                : TokenRedemptionFailureReason.TOKEN_EXPIRED;
    }
}
