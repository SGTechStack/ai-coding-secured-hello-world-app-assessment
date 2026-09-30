package sg.securedhello.audit;

/** Why a session was ended by another sign-in (row 10). */
public enum SessionEvictionReason implements AuditReason {

    /** A newer sign-in of the same account displaced it: one session per account, the new login wins (R-AUTH-002). */
    CONCURRENT_EVICTION("CONCURRENT_EVICTION");

    private final String code;

    SessionEvictionReason(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
