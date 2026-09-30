package sg.securedhello.audit;

/** Why a signed-in session ended at its limit (row 9). */
public enum SessionTimeoutReason implements AuditReason {

    /** The session reached its absolute lifetime, measured from sign-in (ADR-038). */
    ABSOLUTE_TIMEOUT("ABSOLUTE_TIMEOUT");

    private final String code;

    SessionTimeoutReason(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
