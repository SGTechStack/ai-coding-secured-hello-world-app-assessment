package sg.securedhello.audit;

/**
 * Every key an audit context may write, by its ECS field name. The list is closed: a field that is not named here
 * cannot reach an audit row, and one that is named reaches only the rows whose {@link AuditEvent} allows it (ADR-055).
 *
 * <p>The emitter adds the Tier B constants ({@code event.*}) and, on request-scoped rows, the request fields
 * ({@code url.path}, {@code http.request.method}, {@code source.ip_hash}, {@code session.hash}) itself; a context never
 * writes those.
 */
public enum AuditKey {

    /** The host the application runs on (startup row; LOG §3.1). */
    HOST_NAME("host.name"),
    /** The host's addresses. The server's own, never a client's (ADR-054). */
    HOST_IP("host.ip"),
    /** The active Spring profiles, or {@code default}. */
    ACTIVE_PROFILES("labels.active_profiles"),
    /** The effective IPv6 source-key prefix length, the only marker of a change that breaks correlation (ADR-054). */
    IPV6_PREFIX_LENGTH("labels.ipv6_prefix_length"),
    /** Each application key's property and fingerprint, never the key (R-CFG-022). */
    KEY_FINGERPRINTS("labels.key_fingerprints"),
    /** The effective level of each audit-relevant logger (ADR-057). */
    AUDIT_LOGGERS("labels.audit_loggers");

    private final String field;

    AuditKey(String field) {
        this.field = field;
    }

    /** The ECS field name the value is written under. */
    public String field() {
        return field;
    }
}
