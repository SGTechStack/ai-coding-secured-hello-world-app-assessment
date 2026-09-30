package sg.securedhello.recovery;

import java.io.InputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.NestedExceptionUtils;

import sg.securedhello.SecuredHelloApplication;
import sg.securedhello.audit.RecoveryRunContext;

/**
 * Starts the recovery runner: the application jar itself, run with the application stopped, no web server and the
 * {@code --rebind} argument, inside a planned outage (ADR-072). The same context as the application, through its full
 * refresh, with {@link RunnerMode}'s preconditions; then {@link RecoveryRunner} runs once and the process exits.
 */
public final class RecoveryLauncher {

    /** Exit status: the invocation itself is refused, before any context starts. */
    static final int INVALID = 2;
    /** Exit status: the context did not start, for example the database is missing, held or at another version. */
    static final int NOT_STARTED = 1;
    /** Exit status: an unexpected failure after the context started; the audit file says how far it got. */
    static final int ERROR = 5;

    private RecoveryLauncher() {
    }

    /** Whether {@code args} start the runner rather than the application: the argument, never the environment. */
    public static boolean requested(String... args) {
        return RecoveryInvocation.requested(new DefaultApplicationArguments(args));
    }

    /** Runs the runner once and returns the process exit status: 0 on success or a dry run, non-zero otherwise. */
    public static int run(String[] args, InputStream stdin, PrintStream out, PrintStream err) {
        ApplicationArguments arguments = new DefaultApplicationArguments(args);
        RecoveryInvocation invocation;
        try {
            invocation = RecoveryInvocation.parse(arguments);
        } catch (RecoveryRefusedException refusal) {
            err.println("Recovery runner refused: " + refusal.getMessage());
            return INVALID;
        }
        SpringApplication application = new SpringApplication(SecuredHelloApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.addInitializers(new RunnerMode());
        ConfigurableApplicationContext context;
        try {
            context = application.run(args);
        } catch (RuntimeException failure) {
            err.println("Recovery runner refused: the application context did not start, and nothing changed: "
                    + message(failure));
            return NOT_STARTED;
        }
        try (context) {
            return context.getBean(RecoveryRunner.class).run(invocation,
                    operator(invocation.operator(), ProcessHandle.current().info()), stdin, out, err);
        } catch (RuntimeException failure) {
            err.println("Recovery runner stopped unexpectedly: " + message(failure) + ". Check the account state,"
                    + " and the audit file for an intent row with no outcome row (R-RUN-010)");
            return ERROR;
        }
    }

    /**
     * The operator and the process: the claimed id as typed, the OS user only as {@code ProcessHandle} reports it,
     * never {@code user.name}, which the caller can set (T-RUN-001).
     */
    static RecoveryRunContext.Operator operator(String claimedId, ProcessHandle.Info process) {
        return new RecoveryRunContext.Operator(claimedId, process.user().orElse(null), hostName(),
                Path.of("").toAbsolutePath().toString());
    }

    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }

    /** The most specific cause's message, or its type when it has none. */
    private static String message(Throwable failure) {
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(failure);
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getName();
    }
}
