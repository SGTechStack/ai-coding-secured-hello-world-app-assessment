package sg.securedhello.audit;

/**
 * The typed context of one audit row (ADR-055 constraint 1). Each family is a record carrying only UUIDs, enums,
 * numbers and server-derived text, never a map or a domain object; the record writes its keys through
 * {@link AuditFields}, whose typed methods are the only way a value reaches a row.
 */
@FunctionalInterface
public interface AuditContext {

    /** The context of an event that carries no keys and no reason. */
    AuditContext NONE = fields -> {
    };

    /** Writes this context's keys, and its reason if it has one. */
    void writeTo(AuditFields fields);
}
