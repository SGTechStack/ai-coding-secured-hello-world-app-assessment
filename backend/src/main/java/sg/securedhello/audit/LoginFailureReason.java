package sg.securedhello.audit;

/**
 * Why a sign-in failed (row 2). The wire answer is the same 401 for all of them (ADR-033); only this row tells them
 * apart, at WARN.
 */
public enum LoginFailureReason implements AuditReason {

    /** The account exists and the password did not match. */
    BAD_CREDENTIALS("BAD_CREDENTIALS"),
    /** No account has the submitted username. The row carries no {@code user.id}. */
    UNKNOWN_USER("UNKNOWN_USER"),
    /** The account is locked (ADR-011). */
    ACCOUNT_LOCKED("ACCOUNT_LOCKED"),
    /** The account is disabled, or has never been activated. */
    ACCOUNT_DISABLED("ACCOUNT_DISABLED"),
    /** The account's credential has expired (ADR-046). */
    CREDENTIAL_EXPIRED("CREDENTIAL_EXPIRED");

    private final String code;

    LoginFailureReason(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
