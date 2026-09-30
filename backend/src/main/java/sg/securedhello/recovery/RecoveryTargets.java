package sg.securedhello.recovery;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;

import sg.securedhello.audit.RecoveryRunContext;
import sg.securedhello.user.Identifiers;
import sg.securedhello.user.Tombstones;

/**
 * The first step of a recovery run (ADR-072; ADR-074): reads the state of every account an invocation names, checks
 * each is admissible for the scope, and returns it as the {@link RecoveryPlan} the digest is taken over. It writes
 * nothing.
 *
 * <p>{@code password} needs a live, untombstoned account; {@code totp} needs a factor row, in any lock state. A healthy
 * account is admitted: the lost authenticator is the case this exists for (T-RUN-008; T-RUN-009). The batch form checks
 * its cap and every account before anything is written, and refuses as a whole.
 */
final class RecoveryTargets {

    /** The H2 database file's suffix after the path {@code DATABASE_PATH()} reports. */
    private static final String H2_FILE_SUFFIX = ".mv.db";

    /** The batch input file's size limit: {@value RecoveryRunner#BATCH_CAP} usernames of 32 characters fit easily. */
    private static final long BATCH_FILE_MAX_BYTES = 64 * 1024;

    private static final String ACCOUNT_STATE = """
            SELECT u.id, u.username, u.password_hash IS NOT NULL AS credential_set, u.password_disabled_at,
                   u.credential_issued_at, t.created_at AS factor_created_at
            FROM users u LEFT JOIN totp_user_details t ON t.user_id = u.id
            WHERE u.username = ?""";

    private final JdbcTemplate jdbc;
    private final Flyway flyway;
    private final Tombstones tombstones;

    RecoveryTargets(JdbcTemplate jdbc, Flyway flyway, Tombstones tombstones) {
        this.jdbc = jdbc;
        this.flyway = flyway;
        this.tombstones = tombstones;
    }

    /**
     * Reads and checks the named accounts' state.
     *
     * @throws RecoveryRefusedException when an account, the batch file or the database is not admissible
     */
    RecoveryPlan plan(RecoveryInvocation invocation) {
        RecoveryRunContext.Database database = database();
        if (invocation.username() != null) {
            return new RecoveryPlan(invocation.scope(),
                    List.of(admissible(invocation.scope(), invocation.username())), null, database);
        }
        byte[] input = batchInput(invocation.batch());
        // Canonical before the duplicate check, so two spellings of one account cannot both pass (ADR-045).
        List<String> usernames = new String(input, StandardCharsets.UTF_8).lines().map(String::strip)
                .map(line -> line.startsWith("\uFEFF") ? line.substring(1) : line)
                .filter(line -> !line.isEmpty()).map(Identifiers::canonical).toList();
        if (usernames.isEmpty() || usernames.size() > RecoveryRunner.BATCH_CAP) {
            throw new RecoveryRefusedException("the batch file must name 1 to " + RecoveryRunner.BATCH_CAP
                    + " usernames, one per line; it names " + usernames.size());
        }
        if (new HashSet<>(usernames).size() != usernames.size()) {
            throw new RecoveryRefusedException("the batch file names an account more than once");
        }
        List<RecoveryPlan.Account> states = new ArrayList<>();
        List<String> refusals = new ArrayList<>();
        for (String username : usernames) {
            try {
                RecoveryPlan.Account account = admissible(invocation.scope(), username);
                if (invocation.scope() == RecoveryScope.PASSWORD && !account.credentialSet()
                        && account.passwordDisabledAt() == null) {
                    // Invalidating it would leave its tuple as it was, so a leftover confirm could match again.
                    throw new RecoveryRefusedException(username + " has no password to invalidate");
                }
                states.add(account);
            } catch (RecoveryRefusedException refusal) {
                refusals.add(refusal.getMessage());
            }
        }
        if (!refusals.isEmpty()) {
            throw new RecoveryRefusedException("the batch is refused as a whole; nothing changed:\n  "
                    + String.join("\n  ", refusals));
        }
        return new RecoveryPlan(invocation.scope(), states, RecoveryPlan.sha256(input), database);
    }

    private static byte[] batchInput(Path file) {
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > BATCH_FILE_MAX_BYTES) {
                throw new RecoveryRefusedException("the batch file must be a readable file of at most "
                        + BATCH_FILE_MAX_BYTES + " bytes: " + file.toAbsolutePath());
            }
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new RecoveryRefusedException("the batch file cannot be read: " + file.toAbsolutePath());
        }
    }

    /** One account's state, if the scope admits it (ADR-072). */
    private RecoveryPlan.Account admissible(RecoveryScope scope, String submitted) {
        String username = Identifiers.canonical(submitted);
        List<RecoveryPlan.Account> found = jdbc.query(ACCOUNT_STATE, (rs, row) -> new RecoveryPlan.Account(
                rs.getObject("id", UUID.class), rs.getString("username"), rs.getBoolean("credential_set"),
                instant(rs.getObject("password_disabled_at", OffsetDateTime.class)),
                instant(rs.getObject("credential_issued_at", OffsetDateTime.class)),
                instant(rs.getObject("factor_created_at", OffsetDateTime.class))), username);
        if (found.isEmpty()) {
            throw new RecoveryRefusedException(tombstones.holdsUsername(username)
                    ? username + " names a deleted account; its tombstone cannot be recovered"
                    : "no account has the username " + username);
        }
        RecoveryPlan.Account account = found.getFirst();
        if (scope.totp() && !account.hasFactor()) {
            throw new RecoveryRefusedException(username + " has no TOTP factor to clear; use --scope=password");
        }
        return account;
    }

    private static @Nullable Instant instant(@Nullable OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    /** The database's procedural identifiers: what the operator compares with the deployed one (R-RUN-008). */
    private RecoveryRunContext.Database database() {
        String path = jdbc.queryForObject("SELECT DATABASE_PATH()", String.class);
        if (path == null) {
            throw new RecoveryRefusedException("the datasource is not a database file");
        }
        String schema = Optional.ofNullable(flyway.info().current()).map(MigrationInfo::getVersion)
                .map(MigrationVersion::getVersion).orElse("none");
        try {
            // Resolved through any link, so the operator compares the file that is really open (R-RUN-008).
            String file = Path.of(path + H2_FILE_SUFFIX).toRealPath().toString();
            return new RecoveryRunContext.Database(file.substring(0, file.length() - H2_FILE_SUFFIX.length()),
                    schema, Files.getLastModifiedTime(Path.of(file)).toInstant().toString());
        } catch (IOException e) {
            throw new RecoveryRefusedException("the database file cannot be read: " + path + H2_FILE_SUFFIX);
        }
    }
}
