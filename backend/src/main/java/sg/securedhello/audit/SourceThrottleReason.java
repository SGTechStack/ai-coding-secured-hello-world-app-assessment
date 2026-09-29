package sg.securedhello.audit;

/** Why a request was throttled on its source key (row 5). */
public enum SourceThrottleReason implements AuditReason {

    /** A per-source budget in the budget table ran out (ADR-010). */
    RATE_LIMITED_SOURCE("RATE_LIMITED_SOURCE"),
    /** The source's session-store miss budget ran out (ADR-017). */
    RATE_LIMITED_SOURCE_MISSES("RATE_LIMITED_SOURCE_MISSES"),
    /** The source has driven its limit of distinct accounts into lockout (ADR-015). */
    RATE_LIMITED_LOCKOUT_CARDINALITY("RATE_LIMITED_LOCKOUT_CARDINALITY"),
    /**
     * A shed episode started: new anonymous sessions are refused while the session rows or the free disk are past
     * their line (ADR-041). Written once per episode, not per refused request.
     */
    DISK_RESERVE_SHED("DISK_RESERVE_SHED");

    private final String code;

    SourceThrottleReason(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
