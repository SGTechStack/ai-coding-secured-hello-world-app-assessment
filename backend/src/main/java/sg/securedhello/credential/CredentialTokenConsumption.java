package sg.securedhello.credential;

import java.util.Optional;

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
}
