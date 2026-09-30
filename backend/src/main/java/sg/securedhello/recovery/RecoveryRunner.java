package sg.securedhello.recovery;

import java.io.InputStream;
import java.io.PrintStream;

import org.flywaydb.core.Flyway;
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
import sg.securedhello.user.Tombstones;
import sg.securedhello.user.UserAccountRepository;

/**
 * The offline recovery runner (ADR-072; ADR-073; ADR-074): rebinds a password, clears a TOTP factor, or both, for an
 * account when no admin can do it in-app. Only in a context with no web server, the one {@link RecoveryLauncher}
 * starts with the application stopped.
 *
 * <p>One run, in order:
 * <ol>
 *   <li>read the state of every named account and check it is admissible for the scope ({@link RecoveryTargets});</li>
 *   <li>digest that state ({@link RecoveryPlan}). Without {@code --confirm} that is the whole run: a dry run, with its
 *       own audit row. With it, a digest that does not match refuses (T-RUN-011);</li>
 *   <li>single form, {@code password} scope: read the operator's password from a prompt or stdin, never an argument;
 *       </li>
 *   <li>attach the audit file and write the intent row, then apply everything in one transaction
 *       ({@link RecoveryApplier}), then write the outcome row (REJ-090; R-RUN-010).</li>
 * </ol>
 * The apply bypasses {@code AdminActionGuard} on purpose ({@link RecoveryApplier}): with the application stopped there
 * is no concurrent guard to race and no signed-in actor for the self-action check. The guard's count, the enrolled
 * admins, is audited before and after instead, and a transition to zero is the alert (ADR-072; ADR-048).
 *
 * <p>The operator's output is {@link RecoveryReport}'s. No secret is written to any stream (T-AUD-027).
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

    /** The batch form's cap, checked against the whole input before any write (ADR-072). */
    static final int BATCH_CAP = 500;

    private static final Logger log = LoggerFactory.getLogger(RecoveryRunner.class);

    private final RecoveryTargets targets;
    private final RecoveryApplier applier;
    private final AuthenticableAdmins admins;
    private final AuditEmitter audit;

    RecoveryRunner(JdbcTemplate jdbc, Flyway flyway, Tombstones tombstones, AuthenticableAdmins admins,
            UserAccountRepository accounts, PasswordService passwords, TotpFactorRemoval factors,
            SessionTerminationService sessions, AuditEmitter audit, PlatformTransactionManager transactionManager) {
        this.targets = new RecoveryTargets(jdbc, flyway, tombstones);
        this.applier = new RecoveryApplier(accounts, passwords, factors, sessions,
                new TransactionTemplate(transactionManager));
        this.admins = admins;
        this.audit = audit;
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
            plan = targets.plan(invocation);
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
            RecoveryReport.planned(out, plan, enrolledBefore);
            return DONE;
        }
        audit.emit(AuditEvent.RECOVERY_STARTED, context(invocation, operator, plan, enrolledBefore, null));
        try {
            applier.apply(plan, password);
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
        RecoveryReport.applied(out, invocation, plan, enrolledBefore, enrolledAfter);
        return DONE;
    }

    private int failed(RecoveryInvocation invocation, RecoveryRunContext.Operator operator, RecoveryPlan plan,
            long enrolledBefore, PrintStream err, String cause) {
        audit.emit(AuditEvent.RECOVERY_FAILED,
                context(invocation, operator, plan, enrolledBefore, admins.enrolledCount()));
        err.println("Recovery runner failed and rolled back; nothing changed: " + cause);
        return FAILED;
    }

    private static RecoveryRunContext context(RecoveryInvocation invocation, RecoveryRunContext.Operator operator,
            RecoveryPlan plan, long enrolledBefore, @Nullable Long enrolledAfter) {
        return new RecoveryRunContext(operator, invocation.scope().code(), plan.digest(), plan.targets(),
                plan.database(), enrolledBefore, enrolledAfter, invocation.reason());
    }
}
