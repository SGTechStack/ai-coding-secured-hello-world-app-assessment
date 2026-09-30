package sg.securedhello.audit;

/**
 * Why a submitted activation or reset token did not redeem (row 20). The wire answer is the same for all three
 * (ADR-032).
 */
public enum TokenRedemptionFailureReason implements AuditReason {

    /** No token of that type has the submitted value's hash, or the value cannot be a minted token. */
    TOKEN_UNKNOWN("TOKEN_UNKNOWN"),

    /** The token was never used and has expired, or was cancelled by an expiry in place. */
    TOKEN_EXPIRED("TOKEN_EXPIRED"),

    /** The token was already used. */
    TOKEN_CONSUMED("TOKEN_CONSUMED");

    private final String code;

    TokenRedemptionFailureReason(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
