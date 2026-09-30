package sg.securedhello.audit;

/**
 * Why the admin surface asked for the second factor. One reason, two spellings: the constant's name is the 412
 * {@code MISSING_FACTOR} problem's {@code reason} member (R-MFA-001), and {@link #code()} is row 14's audit reason.
 */
public enum FactorRequiredReason implements AuditReason {

    /** The session does not hold the TOTP factor. */
    MISSING("FACTOR_MISSING"),

    /** The session holds the factor, but longer ago than the rule accepts (ADR-021). */
    EXPIRED("FACTOR_EXPIRED");

    private final String code;

    FactorRequiredReason(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
