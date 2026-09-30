package sg.securedhello.audit;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * The context of the recovery runner's rows (ADR-072; ADR-074; REJ-090): who claims to run it and where, what it
 * targets, the database it ran against and the enrolled-admin count. No row carries a command line or its arguments
 * (T-AUD-029), and none carries a secret: the digest hashes non-secret state and is printed anyway.
 *
 * @param operator            the operator and the process
 * @param scope               {@code password}, {@code totp} or {@code both}
 * @param digest              the plan/apply digest
 * @param targets             the accounts, by UUID; {@code user.target.id} is written when there is exactly one
 * @param database            the database's procedural identifiers (R-RUN-008)
 * @param enrolledAdminsBefore the enrolled admins before the run (ADR-072)
 * @param enrolledAdminsAfter the enrolled admins after it, on an outcome row only
 * @param reason              the operator's {@code --reason}, on an apply row only
 */
public record RecoveryRunContext(Operator operator, String scope, String digest, List<UUID> targets,
        Database database, long enrolledAdminsBefore, @Nullable Long enrolledAdminsAfter, @Nullable String reason)
        implements AuditContext {

    /** The longest {@code --reason} the row carries, so an operator's paste cannot bloat the audit file. */
    public static final int REASON_MAX_LENGTH = 256;

    /** The keys every runner row carries. */
    static final AuditKey[] COMMON_KEYS = {AuditKey.OPERATOR_CLAIMED_ID, AuditKey.HOST_NAME,
            AuditKey.WORKING_DIRECTORY, AuditKey.RUNNER_SCOPE, AuditKey.RUNNER_DIGEST, AuditKey.USER_TARGET_COUNT,
            AuditKey.DATABASE_PATH, AuditKey.DATABASE_SCHEMA_VERSION, AuditKey.DATABASE_MODIFIED,
            AuditKey.ENROLLED_ADMINS_BEFORE};

    public RecoveryRunContext {
        targets = List.copyOf(targets);
    }

    /**
     * The operator and the process they ran.
     *
     * @param claimedId        {@code --operator}, format-checked only (R-AUD-033)
     * @param realUser         the OS user {@code ProcessHandle} reports, or {@code null} when it reports none
     * @param hostName         the host
     * @param workingDirectory the process's working directory
     */
    public record Operator(String claimedId, @Nullable String realUser, String hostName, String workingDirectory) {
    }

    /**
     * The database the run read and changed: what the operator compares with the deployed one (R-RUN-008).
     *
     * @param path          the resolved absolute path
     * @param schemaVersion the applied schema version
     * @param modifiedAt    the database file's modification time
     */
    public record Database(String path, String schemaVersion, String modifiedAt) {
    }

    @Override
    public void writeTo(AuditFields fields) {
        fields.put(AuditKey.OPERATOR_CLAIMED_ID, operator.claimedId())
                .put(AuditKey.HOST_NAME, operator.hostName())
                .put(AuditKey.WORKING_DIRECTORY, operator.workingDirectory())
                .put(AuditKey.RUNNER_SCOPE, scope)
                .put(AuditKey.RUNNER_DIGEST, digest)
                .put(AuditKey.USER_TARGET_COUNT, targets.size())
                .put(AuditKey.DATABASE_PATH, database.path())
                .put(AuditKey.DATABASE_SCHEMA_VERSION, database.schemaVersion())
                .put(AuditKey.DATABASE_MODIFIED, database.modifiedAt())
                .put(AuditKey.ENROLLED_ADMINS_BEFORE, enrolledAdminsBefore);
        if (operator.realUser() != null) {
            fields.put(AuditKey.PROCESS_REAL_USER, operator.realUser());
        }
        if (targets.size() == 1) {
            fields.put(AuditKey.USER_TARGET_ID, targets.getFirst());
        }
        if (enrolledAdminsAfter != null) {
            fields.put(AuditKey.ENROLLED_ADMINS_AFTER, enrolledAdminsAfter);
        }
        if (reason != null) {
            fields.put(AuditKey.RUNNER_REASON, reason);
        }
    }
}
