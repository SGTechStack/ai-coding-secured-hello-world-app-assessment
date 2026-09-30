package sg.securedhello.audit;

/** Why a request's session cookie was not honoured as presented (row 11). */
public enum InvalidSessionReason implements AuditReason {

    /**
     * The presented session id resolved to no live session: expired, ended, deleted or never issued. Idle expiry
     * cannot be told apart from the rest (R-AUD-003).
     */
    UNKNOWN_OR_EXPIRED("UNKNOWN_OR_EXPIRED"),

    /** More than one session cookie arrived; only the first is honoured (R-SES-007). */
    DUPLICATE_SESSION_COOKIE("DUPLICATE_SESSION_COOKIE");

    private final String code;

    InvalidSessionReason(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
