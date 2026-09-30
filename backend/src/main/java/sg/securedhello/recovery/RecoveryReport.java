package sg.securedhello.recovery;

import java.io.PrintStream;

/**
 * What a recovery run tells the operator on standard output: the dry run's plan and digest, or what an apply changed
 * (ADR-074). Only procedural identifiers: never a username, a password or any other secret (T-AUD-027).
 */
final class RecoveryReport {

    private RecoveryReport() {
    }

    /** The dry run's output: the identifiers to compare, the enrolled-admin count and the digest to confirm. */
    static void planned(PrintStream out, RecoveryPlan plan, long enrolledBefore) {
        out.println("Recovery runner: dry run; nothing changed.");
        identifiers(out, plan);
        out.println("  enrolled admins now: " + enrolledBefore);
        out.println("  digest:              " + plan.digest());
        out.println("Compare the database path, schema version and modification time with the deployed database,"
                + " then apply with --confirm=" + plan.digest() + " --reason=\"...\" (ADR-074).");
    }

    /** An apply's output: the identifiers, the enrolled-admin counts, and what each account does next. */
    static void applied(PrintStream out, RecoveryInvocation invocation, RecoveryPlan plan, long enrolledBefore,
            long enrolledAfter) {
        out.println("Recovery runner: applied.");
        identifiers(out, plan);
        out.println("  enrolled admins:     " + enrolledBefore + " before, " + enrolledAfter + " after");
        if (plan.scope().password()) {
            out.println(invocation.username() != null
                    ? "The account must change this password at its next sign-in, within 30 days (ADR-046)."
                    : "The passwords are invalidated; each account recovers through a password reset (ADR-073).");
        }
        if (plan.scope().totp()) {
            out.println("The TOTP factor is cleared; an administrator enrols again at the next sign-in.");
        }
    }

    private static void identifiers(PrintStream out, RecoveryPlan plan) {
        out.println("  scope:               " + plan.scope().code());
        plan.accounts().forEach(account -> out.println("  account:             " + account.id()));
        out.println("  database:            " + plan.database().path());
        out.println("  schema version:      " + plan.database().schemaVersion());
        out.println("  database modified:   " + plan.database().modifiedAt());
    }
}
