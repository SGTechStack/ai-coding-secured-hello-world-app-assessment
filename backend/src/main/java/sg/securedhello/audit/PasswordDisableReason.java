package sg.securedhello.audit;

/** Why a password authenticator was disabled. */
public enum PasswordDisableReason implements AuditReason {

    /** Consecutive failures since the last success reached the NIST cap (ADR-013). */
    FAILURE_CAP("FAILURE_CAP");

    private final String code;

    PasswordDisableReason(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
