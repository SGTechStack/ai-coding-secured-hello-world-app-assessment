package sg.securedhello.audit;

/** Which keying tier reached its distinct-key cap in a keying window (row 46; ADR-019). */
public enum TruncationReason implements AuditReason {

    /** Tier 1 tracked {@code app.audit.truncation.distinct-sources} source keys, and more arrived. */
    SOURCE_CAP_REACHED("SOURCE_CAP_REACHED"),
    /** Tier 2 tracked {@code app.audit.truncation.distinct-users} users, and more arrived. */
    USER_CAP_REACHED("USER_CAP_REACHED");

    private final String code;

    TruncationReason(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
