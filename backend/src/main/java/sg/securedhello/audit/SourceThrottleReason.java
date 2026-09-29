package sg.securedhello.audit;

/** Why a request was throttled on its source key (row 5). */
public enum SourceThrottleReason implements AuditReason {

    /** A per-source budget in the budget table ran out (ADR-010). */
    RATE_LIMITED_SOURCE("RATE_LIMITED_SOURCE"),
    /** The source's session-store miss budget ran out (ADR-017). */
    RATE_LIMITED_SOURCE_MISSES("RATE_LIMITED_SOURCE_MISSES");

    private final String code;

    SourceThrottleReason(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
