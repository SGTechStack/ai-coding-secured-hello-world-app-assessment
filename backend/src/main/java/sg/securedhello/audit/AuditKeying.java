package sg.securedhello.audit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import sg.securedhello.audit.AuditRowDefinition.Keying;

/**
 * The emitter-side bound on audit volume (ADR-019): keyed rows, and a distinct-key cap per keying tier. A limiter cannot
 * do this, because volume is rate times distinct sources, and the attacker picks the second factor.
 *
 * <p>An occurrence of a keyed row is recorded, not written. Within one keying window, occurrences with the same row,
 * reason and key (the source key's hash in tier 1, {@code user.id} in tier 2) collapse into one entry that keeps the
 * first occurrence's fields, a count and the first one's time. When the window closes, each entry is written once
 * with {@code event.count} and {@code event.start} (REJ-078).
 *
 * <p>Each tier admits at most its cap of distinct keys per window. A key beyond the cap is never admitted, so nothing
 * is evicted and no key can emit twice: the memory bound and the evidence bound are the same number. Its occurrences
 * are counted, and the window's close writes one truncation row per capped tier with the exact tracked count and
 * the exact untracked count (REJ-079).
 *
 * <p>A window opens at the first keyed occurrence after the last one closed, and closes when an occurrence or the
 * periodic tick finds it {@code window} old, or when the application stops. All times come from the injected
 * {@code Clock}.
 */
final class AuditKeying {

    /** Where closed-window rows go: the emitter's writer. */
    interface Sink {

        /** Writes a keyed row with its fields, count and first-seen time already added. */
        void keyed(AuditRowDefinition row, Map<String, Object> fields);

        /** Writes the truncation row for one tier. */
        void truncated(TruncationContext context);
    }

    /** The count of occurrences a keyed row stands for. */
    static final String COUNT_FIELD = "event.count";

    /** When the first of them happened. */
    static final String FIRST_SEEN_FIELD = "event.start";

    private final Clock clock;
    private final Duration window;
    private final Map<Keying, Integer> caps = new EnumMap<>(Keying.class);
    private final Sink sink;

    private Instant windowStart;
    private final Map<EntryKey, Entry> entries = new LinkedHashMap<>();
    private final Map<Keying, Tier> tiers = new EnumMap<>(Keying.class);

    AuditKeying(Clock clock, Duration window, int distinctSources, int distinctUsers, Sink sink) {
        this.clock = clock;
        this.window = window;
        this.caps.put(Keying.SOURCE, distinctSources);
        this.caps.put(Keying.USER, distinctUsers);
        this.sink = sink;
    }

    /**
     * Records one occurrence of {@code row}, whose fields are complete and valid. A row with no value for its key field
     * cannot be keyed, and is written at once.
     */
    synchronized void record(String name, AuditRowDefinition row, Map<String, Object> fields) {
        closeIfDue();
        Object key = fields.get(row.keying().keyField());
        if (key == null) {
            sink.keyed(row, fields);
            return;
        }
        Instant now = clock.instant();
        if (windowStart == null) {
            windowStart = now;
        }
        Tier tier = tiers.computeIfAbsent(row.keying(), unused -> new Tier());
        if (!tier.keys.contains(key)) {
            if (tier.keys.size() >= caps.get(row.keying())) {
                tier.untracked++;
                tier.truncatedRows.add(name);
                return;
            }
            tier.keys.add(key);
        }
        entries.computeIfAbsent(new EntryKey(name, fields.get("event.reason"), key),
                unused -> new Entry(row, fields, now)).count++;
    }

    /** Closes the open window if it has lasted {@code window}. */
    synchronized void closeIfDue() {
        if (windowStart != null && !clock.instant().isBefore(windowStart.plus(window))) {
            close();
        }
    }

    /** Writes the open window's keyed rows, then its truncation rows, and starts afresh. */
    synchronized void close() {
        entries.values().forEach(entry -> {
            Map<String, Object> fields = new LinkedHashMap<>(entry.fields);
            fields.put(COUNT_FIELD, entry.count);
            fields.put(FIRST_SEEN_FIELD, entry.firstSeen.toString());
            sink.keyed(entry.row, fields);
        });
        tiers.forEach((keying, tier) -> {
            if (tier.untracked > 0) {
                sink.truncated(new TruncationContext(keying == Keying.SOURCE ? TruncationReason.SOURCE_CAP_REACHED
                        : TruncationReason.USER_CAP_REACHED, tier.keys.size(), tier.untracked,
                        List.copyOf(tier.truncatedRows)));
            }
        });
        entries.clear();
        tiers.clear();
        windowStart = null;
    }

    private record EntryKey(String name, Object reason, Object key) {
    }

    private static final class Entry {

        private final AuditRowDefinition row;
        private final Map<String, Object> fields;
        private final Instant firstSeen;
        private long count;

        Entry(AuditRowDefinition row, Map<String, Object> fields, Instant firstSeen) {
            this.row = row;
            this.fields = fields;
            this.firstSeen = firstSeen;
        }
    }

    /** One tier's window: the keys admitted, and what arrived from keys beyond the cap. */
    private static final class Tier {

        private final Set<Object> keys = new LinkedHashSet<>();
        private final Set<String> truncatedRows = new TreeSet<>();
        private long untracked;
    }
}
