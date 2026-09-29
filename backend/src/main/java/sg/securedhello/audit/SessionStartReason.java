package sg.securedhello.audit;

/** What rotated the session id at authentication (row 8). The factor-grant and password-change reasons come later. */
public enum SessionStartReason implements AuditReason {

    /** Password sign-in, the one place {@code AUTH_INSTANT} is stamped (ADR-038). */
    LOGIN("LOGIN");

    private final String code;

    SessionStartReason(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
