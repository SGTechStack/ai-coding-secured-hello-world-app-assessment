package sg.securedhello.credential;

import java.io.Serial;

import sg.securedhello.audit.TokenRedemptionFailureReason;

/**
 * A submitted credential token did not redeem: unknown, of another type, already used or expired. Every such failure
 * is one answer, 400 {@code RESET_TOKEN_INVALID}, whatever the token's type, so an anonymous caller never learns which
 * kind of token it holds (ADR-032). Only the audit row (row 20) carries the {@linkplain #reason() reason}. Build one
 * with {@link CredentialTokens#refused}.
 */
public class CredentialTokenInvalidException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final TokenRedemptionFailureReason reason;

    CredentialTokenInvalidException(TokenRedemptionFailureReason reason) {
        super("The credential token did not redeem");
        this.reason = reason;
    }

    /**
     * A token that redeemed in this transaction for an account that is no longer there, which rolls the redemption
     * back. The token is consumed only inside that transaction, so no stored state names it better than
     * {@code TOKEN_UNKNOWN}.
     */
    public static CredentialTokenInvalidException accountGone() {
        return new CredentialTokenInvalidException(TokenRedemptionFailureReason.TOKEN_UNKNOWN);
    }

    /** Why the token did not redeem, for the audit row only. */
    public TokenRedemptionFailureReason reason() {
        return reason;
    }
}
