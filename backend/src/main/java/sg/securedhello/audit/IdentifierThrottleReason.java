package sg.securedhello.audit;

/** Why a request was throttled on the value it submitted (row 6). */
public enum IdentifierThrottleReason implements AuditReason {

    /** A per-submitted-value budget, such as the login username axis, ran out (ADR-010). */
    RATE_LIMITED_IDENTIFIER("RATE_LIMITED_IDENTIFIER");

    private final String code;

    IdentifierThrottleReason(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
