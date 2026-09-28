package sg.securedhello.audit;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What an {@link AuditContext} writes: values keyed by {@link AuditKey}, and at most one {@link AuditReason}. Only
 * UUIDs, enums, numbers, booleans and text are accepted. The emitter checks the keys against the event before
 * anything is written, and strips CR, LF and pipe from every text value (ASVS 16.4.1).
 */
public final class AuditFields {

    private final Map<AuditKey, Object> values = new EnumMap<>(AuditKey.class);
    private AuditReason reason;

    AuditFields() {
    }

    public AuditFields put(AuditKey key, UUID value) {
        return set(key, value.toString());
    }

    public AuditFields put(AuditKey key, Enum<?> value) {
        return set(key, value.name());
    }

    public AuditFields put(AuditKey key, long value) {
        return set(key, value);
    }

    public AuditFields put(AuditKey key, boolean value) {
        return set(key, value);
    }

    /** Server-derived text only; a client-supplied string has no place in a context record. */
    public AuditFields put(AuditKey key, String value) {
        return set(key, AuditText.sanitise(value));
    }

    /** Server-derived text only, as a JSON array. */
    public AuditFields put(AuditKey key, List<String> values) {
        return set(key, values.stream().map(AuditText::sanitise).toList());
    }

    /** The row's {@code event.reason}, which must belong to the event's family. */
    public AuditFields reason(AuditReason value) {
        this.reason = value;
        return this;
    }

    Map<AuditKey, Object> values() {
        return Collections.unmodifiableMap(values);
    }

    AuditReason reason() {
        return reason;
    }

    private AuditFields set(AuditKey key, Object value) {
        values.put(key, value);
        return this;
    }
}
