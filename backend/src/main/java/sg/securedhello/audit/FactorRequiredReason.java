package sg.securedhello.audit;

/** Why the admin surface asked a signed-in administrator for the second factor (row 14). */
public enum FactorRequiredReason implements AuditReason {

    /** The session does not hold the TOTP factor. */
    FACTOR_MISSING("FACTOR_MISSING"),

    /** The session holds the factor, but longer ago than the rule accepts (ADR-021). */
    FACTOR_EXPIRED("FACTOR_EXPIRED");

    private final String code;

    FactorRequiredReason(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
