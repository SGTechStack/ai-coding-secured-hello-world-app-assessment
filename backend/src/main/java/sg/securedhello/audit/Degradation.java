package sg.securedhello.audit;

/**
 * Why the emitter wrote a degraded row instead of the requested one (ADR-055 constraint 4): the reason family of the
 * degraded row, whose {@code event.outcome} is {@code unknown}. Serialised by {@link #code()} to
 * {@code event.reason}, and pinned with the other reason codes (T-AUD-045).
 */
public enum Degradation implements AuditReason {

    /** The context wrote a key the event does not allow. The key and its value are dropped, not written. */
    UNKNOWN_KEY("UNKNOWN_KEY"),
    /** The context left out a key the event requires, or a request-scoped event was emitted outside a request. */
    MISSING_KEY("MISSING_KEY"),
    /** The reason is missing, or belongs to another event's family. */
    REASON_OUTSIDE_FAMILY("REASON_OUTSIDE_FAMILY"),
    /** Building the row failed. */
    EMIT_FAILED("EMIT_FAILED");

    private final String code;

    Degradation(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
