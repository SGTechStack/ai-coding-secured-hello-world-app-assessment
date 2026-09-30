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
    AUDIT_LOGGERS("labels.audit_loggers"),
    /**
     * The account the row is about, by its UUID; never a username or email (ADR-054). On a failed login it is written
     * only when the account resolves, and set explicitly by the caller, never from MDC (REJ-042; R-AUD-007).
     */
    USER_ID("user.id"),
    /** The account an administrator acted on or read, by its UUID; the acting admin stays in {@code user.id}. */
    USER_TARGET_ID("user.target.id"),
    /** The number of accounts an admin read returned (a custom field, REJ-044; R-AUD-002). */
    USER_TARGET_COUNT("user.target.count"),
    /** On a truncation row: the source keys the window tracked, exactly; at most the cap (ADR-019; REJ-079). */
    SOURCE_DISTINCT_COUNT("source.distinct_count"),
    /** On a truncation row: the users the window tracked, exactly; at most the cap (ADR-019; REJ-079). */
    USER_DISTINCT_COUNT("user.distinct_count"),
    /** On a truncation row: the occurrences from keys beyond the cap, exactly (ADR-019; REJ-079). */
    EVENTS_UNTRACKED_COUNT("events.untracked_count"),
    /** On a truncation row: which rows had occurrences beyond the cap, by event name. */
    TRUNCATED_ROWS("labels.truncated_rows"),
    /**
     * On a recovery-runner row: the operator's {@code --operator} value, in clear, format-checked and never resolved
     * against accounts (R-AUD-033). An unverified claim; the host's sudo or ssh trail is the attribution (R-OPS-007).
     */
    OPERATOR_CLAIMED_ID("labels.operator_claimed_id"),
    /**
     * On a recovery-runner row: the OS user {@code ProcessHandle} reports for the runner process. Omitted when it
     * reports none; {@code user.name} is never a fallback (T-RUN-001; T-RUN-002).
     */
    PROCESS_REAL_USER("process.real_user.name"),
    /** On a recovery-runner row: the runner's working directory, in clear (R-RUN-010). */
    WORKING_DIRECTORY("labels.working_directory"),
    /** On a recovery-runner row: {@code password}, {@code totp} or {@code both} (ADR-072). */
    RUNNER_SCOPE("labels.runner_scope"),
    /** On a recovery-runner row: the plan/apply digest over the account state, which is not a secret (ADR-074). */
    RUNNER_DIGEST("labels.runner_digest"),
    /** On a recovery-runner apply row: the operator's mandatory {@code --reason} (ADR-074). */
    RUNNER_REASON("labels.runner_reason"),
    /** On a recovery-runner row: the resolved absolute database path (ADR-074; R-RUN-008). */
    DATABASE_PATH("labels.database_path"),
    /** On a recovery-runner row: the database's schema version (ADR-074; R-RUN-008). */
    DATABASE_SCHEMA_VERSION("labels.database_schema_version"),
    /** On a recovery-runner row: the database file's modification time (ADR-074; R-RUN-008). */
    DATABASE_MODIFIED("labels.database_modified"),
    /** On a recovery-runner row: the enrolled admins before the run, the guard's count (ADR-072; ADR-048). */
    ENROLLED_ADMINS_BEFORE("labels.enrolled_admins_before"),
    /** On a recovery-runner outcome row: the enrolled admins after the run; zero is the alert (ADR-072). */
    ENROLLED_ADMINS_AFTER("labels.enrolled_admins_after");

    private final String field;

    AuditKey(String field) {
        this.field = field;
    }

    /** The ECS field name the value is written under. */
    public String field() {
        return field;
    }
}
