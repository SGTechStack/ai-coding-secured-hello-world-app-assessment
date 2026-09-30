package sg.securedhello.recovery;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import sg.securedhello.admin.AuthenticableAdmins;
import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.audit.AuditEvent;
import sg.securedhello.audit.RecoveryRunContext;
import sg.securedhello.mfa.TotpFactorRemoval;
import sg.securedhello.password.PasswordRejectedException;
import sg.securedhello.password.PasswordService;
import sg.securedhello.session.SessionTerminationService;
import sg.securedhello.user.Identifiers;
import sg.securedhello.user.PasswordLockoutState;
import sg.securedhello.user.Tombstones;
import sg.securedhello.user.UserAccount;
import sg.securedhello.user.UserAccountRepository;

/**
 * The offline recovery runner (ADR-072; ADR-073; ADR-074): rebinds a password, clears a TOTP factor, or both, for an
 * account when no admin can do it in-app. Only in a context with no web server, the one {@link RecoveryLauncher}
 * starts with the application stopped.
 *
 * <p>One run, in order:
 * <ol>
 *   <li>read the state of every named account and check it is admissible for the scope: {@code password} needs a live,
 *       untombstoned account; {@code totp} needs a factor row, in any lock state. A healthy account is admitted: the
 *       lost authenticator is the case this exists for (T-RUN-008; T-RUN-009). The batch form checks its cap and every
 *       account before anything is written;</li>
 *   <li>digest that state ({@link RecoveryPlan}). Without {@code --confirm} that is the whole run: a dry run, with its
 *       own audit row. With it, a digest that does not match refuses (T-RUN-011);</li>
 *   <li>single form, {@code password} scope: read the operator's password from a prompt or stdin, never an argument;
 *       </li>
 *   <li>attach the audit file and write the intent row, then apply everything in one transaction, then write the
 *       outcome row (REJ-090; R-RUN-010).</li>
 * </ol>
 * It bypasses {@code AdminActionGuard} on purpose: {@code both} must be able to take the system to zero enrolled
 * admins, which is what break-glass means, and with the application stopped there is no concurrent guard to race and
 * no signed-in actor for the self-action check. The guard's count, the enrolled admins, is audited before and after
 * instead, and a transition to zero is the alert (ADR-072; ADR-048).
 *
 * <p>The password goes through {@link PasswordService} with no exception at the call site, as a forced-change
 * credential (ADR-046); the batch form invalidates and mints nothing. No secret is written to any stream (T-AUD-027).
 */
@Component
@ConditionalOnNotWebApplication
class RecoveryRunner {

    /** Exit status: done, or a dry run printed its digest. */
    static final int DONE = 0;
    /** Exit status: refused; nothing changed. */
    static final int REFUSED = 3;
    /** Exit status: the apply failed and rolled back; nothing changed. */
    static final int FAILED = 4;

    /** The H2 database file's suffix after the path {@code DATABASE_PATH()} reports. */
    private static final String H2_FILE_SUFFIX = ".mv.db";

    private static final Logger log = LoggerFactory.getLogger(RecoveryRunner.class);

    /** The batch form's cap, checked against the whole input before any write (ADR-072). */
    static final int BATCH_CAP = 500;

    /** The batch input file's size limit: {@value #BATCH_CAP} usernames of 32 characters fit easily. */
    private static final long BATCH_FILE_MAX_BYTES = 64 * 1024;

    private static final String ACCOUNT_STATE = """
            SELECT u.id, u.username, u.password_hash IS NOT NULL AS credential_set, u.password_disabled_at,
                   u.credential_issued_at, t.created_at AS factor_created_at
            FROM users u LEFT JOIN totp_user_details t ON t.user_id = u.id
            WHERE u.username = ?""";

    private final JdbcTemplate jdbc;
    private final Flyway flyway;
    private final Tombstones tombstones;
    private final AuthenticableAdmins admins;
    private final UserAccountRepository accounts;
    private final PasswordService passwords;
    private final TotpFactorRemoval factors;
    private final SessionTerminationService sessions;
    private final AuditEmitter audit;
    private final TransactionTemplate transactions;

    RecoveryRunner(JdbcTemplate jdbc, Flyway flyway, Tombstones tombstones, AuthenticableAdmins admins,
            UserAccountRepository accounts, PasswordService passwords, TotpFactorRemoval factors,
            SessionTerminationService sessions, AuditEmitter audit, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.flyway = flyway;
        this.tombstones = tombstones;
        this.admins = admins;
        this.accounts = accounts;
        this.passwords = passwords;
        this.factors = factors;
        this.sessions = sessions;
        this.audit = audit;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /**
     * Runs {@code invocation}. Operator messages go to {@code out}, refusals to {@code err}; neither ever carries the
     * password.
     *
     * @return {@link #DONE}, {@link #REFUSED} or {@link #FAILED}
     */
    int run(RecoveryInvocation invocation, RecoveryRunContext.Operator operator, InputStream stdin, PrintStream out,
            PrintStream err) {
        RecoveryPlan plan;
        String password = null;
        try {
            plan = plan(invocation);
            if (!invocation.dryRun() && !plan.digest().equals(invocation.confirm())) {
                throw new RecoveryRefusedException("the account state no longer matches the --confirm digest, or the"
                        + " digest is wrong. Nothing changed. Run the dry run again, check its output, and confirm"
                        + " its digest (ADR-074)");
            }
            if (!invocation.dryRun() && invocation.scope().password() && invocation.username() != null) {
                password = OperatorPassword.read(invocation.nonInteractive(), stdin,
                        plan.accounts().getFirst().username());
            }
        } catch (RecoveryRefusedException refusal) {
            err.println("Recovery runner refused: " + refusal.getMessage());
            return REFUSED;
        }
        long enrolledBefore = admins.enrolledCount();
        AuditFileGate.attach();
        if (invocation.dryRun()) {
            audit.emit(AuditEvent.RECOVERY_PLANNED, context(invocation, operator, plan, enrolledBefore, null));
            printPlan(out, invocation, plan, enrolledBefore);
            return DONE;
        }
        audit.emit(AuditEvent.RECOVERY_STARTED, context(invocation, operator, plan, enrolledBefore, null));
        try {
            apply(plan, password);
        } catch (PasswordRejectedException rejected) {
            return failed(invocation, operator, plan, enrolledBefore, err,
                    "the password policy refused the password: rule " + rejected.rule() + " (ADR-005)");
        } catch (RuntimeException failure) {
            // The cause is for the operator's diagnosis; the password is never in an exception message.
            log.error("Recovery runner apply failed and rolled back", failure);
            return failed(invocation, operator, plan, enrolledBefore, err,
                    "the apply failed with " + failure.getClass().getName());
        }
        long enrolledAfter = admins.enrolledCount();
        audit.emit(AuditEvent.RECOVERY_COMPLETED, context(invocation, operator, plan, enrolledBefore, enrolledAfter));
        printApplied(out, invocation, plan, enrolledBefore, enrolledAfter);
        return DONE;
    }

    private int failed(RecoveryInvocation invocation, RecoveryRunContext.Operator operator, RecoveryPlan plan,
            long enrolledBefore, PrintStream err, String cause) {
        audit.emit(AuditEvent.RECOVERY_FAILED,
                context(invocation, operator, plan, enrolledBefore, admins.enrolledCount()));
        err.println("Recovery runner failed and rolled back; nothing changed: " + cause);
        return FAILED;
    }

    /** Reads and checks the named accounts' state. */
    private RecoveryPlan plan(RecoveryInvocation invocation) {
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
        if (usernames.isEmpty() || usernames.size() > BATCH_CAP) {
            throw new RecoveryRefusedException("the batch file must name 1 to " + BATCH_CAP + " usernames, one per"
                    + " line; it names " + usernames.size());
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

    /** Everything, in one transaction: a rejected password or any failure rolls every account back. */
    private void apply(RecoveryPlan plan, @Nullable String password) {
        transactions.executeWithoutResult(status -> {
            for (RecoveryPlan.Account target : plan.accounts()) {
                UserAccount account = accounts.findForUpdateById(target.id()).orElseThrow();
                if (plan.scope().password()) {
                    // Before the password write: its reset-token cleanup flushes and clears the persistence context.
                    account.setLockoutState(PasswordLockoutState.CLEAR);
                    if (password != null) {
                        passwords.issueForcedChangeCredential(target.id(), password);
                    } else {
                        passwords.invalidate(target.id());
                    }
                }
                if (plan.scope().totp()) {
                    factors.remove(target.id());
                }
                sessions.endAll(target.username());
            }
        });
    }

    private static RecoveryRunContext context(RecoveryInvocation invocation, RecoveryRunContext.Operator operator,
            RecoveryPlan plan, long enrolledBefore, @Nullable Long enrolledAfter) {
        return new RecoveryRunContext(operator, invocation.scope().code(), plan.digest(), plan.targets(),
                plan.database(), enrolledBefore, enrolledAfter, invocation.reason());
    }

    private static void printPlan(PrintStream out, RecoveryInvocation invocation, RecoveryPlan plan,
            long enrolledBefore) {
        out.println("Recovery runner: dry run; nothing changed.");
        printIdentifiers(out, invocation, plan);
        out.println("  enrolled admins now: " + enrolledBefore);
        out.println("  digest:              " + plan.digest());
        out.println("Compare the database path, schema version and modification time with the deployed database,"
                + " then apply with --confirm=" + plan.digest() + " --reason=\"...\" (ADR-074).");
    }

    private static void printApplied(PrintStream out, RecoveryInvocation invocation, RecoveryPlan plan,
            long enrolledBefore, long enrolledAfter) {
        out.println("Recovery runner: applied.");
        printIdentifiers(out, invocation, plan);
        out.println("  enrolled admins:     " + enrolledBefore + " before, " + enrolledAfter + " after");
        if (invocation.scope().password()) {
            out.println(invocation.username() != null
                    ? "The account must change this password at its next sign-in, within 30 days (ADR-046)."
                    : "The passwords are invalidated; each account recovers through a password reset (ADR-073).");
        }
        if (invocation.scope().totp()) {
            out.println("The TOTP factor is cleared; an administrator enrols again at the next sign-in.");
        }
    }

    private static void printIdentifiers(PrintStream out, RecoveryInvocation invocation, RecoveryPlan plan) {
        out.println("  scope:               " + invocation.scope().code());
        plan.accounts().forEach(account -> out.println("  account:             " + account.id()));
        out.println("  database:            " + plan.database().path());
        out.println("  schema version:      " + plan.database().schemaVersion());
        out.println("  database modified:   " + plan.database().modifiedAt());
    }
}
