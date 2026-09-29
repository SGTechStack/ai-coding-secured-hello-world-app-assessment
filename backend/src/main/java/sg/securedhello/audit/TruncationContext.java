package sg.securedhello.audit;

import java.util.List;

/**
 * The truncation row's context (row 46): the tier that reached its cap, and two exact counts (ADR-019; REJ-079). The
 * true number of distinct keys lies between {@code distinctCount} and {@code distinctCount + untrackedCount}.
 *
 * @param reason         the tier, as its reason
 * @param distinctCount  the keys the window tracked
 * @param untrackedCount the occurrences from keys beyond the cap
 * @param truncatedRows  the rows those occurrences belonged to, by event name
 */
record TruncationContext(TruncationReason reason, long distinctCount, long untrackedCount, List<String> truncatedRows)
        implements AuditContext {

    @Override
    public void writeTo(AuditFields fields) {
        fields.reason(reason)
                .put(reason == TruncationReason.SOURCE_CAP_REACHED ? AuditKey.SOURCE_DISTINCT_COUNT
                        : AuditKey.USER_DISTINCT_COUNT, distinctCount)
                .put(AuditKey.EVENTS_UNTRACKED_COUNT, untrackedCount)
                .put(AuditKey.TRUNCATED_ROWS, truncatedRows);
    }
}
