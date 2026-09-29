package sg.securedhello.audit;

/** Why a CSRF check refused an unsafe request (row 13). */
public enum CsrfReason implements AuditReason {

    /** The session held no token to compare against, typically because there is no session (T-CSRF-008). */
    CSRF_MISSING("CSRF_MISSING"),
    /** A token was stored, and the request's header was absent or did not match it. */
    CSRF_INVALID("CSRF_INVALID");

    private final String code;

    CsrfReason(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
