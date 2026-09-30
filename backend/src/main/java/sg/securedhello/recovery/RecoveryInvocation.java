package sg.securedhello.recovery;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.ApplicationArguments;

import sg.securedhello.audit.RecoveryRunContext;

/**
 * One runner invocation, read from the command line only (ADR-072): the {@value #TRIGGER} trigger is an
 * {@link ApplicationArguments} option, never a property, so a leftover {@code REBIND} environment variable fires
 * nothing (T-RUN-003).
 *
 * <pre>
 * --rebind --scope=password|totp|both (--username=NAME | --batch=FILE) --operator=ID
 *          [--confirm=DIGEST --reason=TEXT] [--non-interactive]
 * </pre>
 * Without {@code --confirm} it is a dry run (ADR-074). No argument carries a secret: any option naming a password is
 * refused, and the single form reads the password from a prompt, or from stdin with {@code --non-interactive}
 * (ADR-073; T-RUN-013). Options with a dot are Spring properties, which the application binds as usual; any other
 * unknown option is refused.
 *
 * @param scope          what the run rebinds
 * @param username       the single form's account, or {@code null} in the batch form
 * @param batch          the batch form's input file, one username per line, or {@code null} in the single form
 * @param operator       the operator's claimed identifier (R-AUD-033)
 * @param confirm        the digest the apply step passes back, or {@code null} for a dry run
 * @param reason         the apply step's mandatory reason, or {@code null} for a dry run
 * @param nonInteractive the password is read from stdin rather than prompted for (ADR-073)
 */
record RecoveryInvocation(RecoveryScope scope, @Nullable String username, @Nullable Path batch, String operator,
        @Nullable String confirm, @Nullable String reason, boolean nonInteractive) {

    /** The option that makes a start a runner start. */
    static final String TRIGGER = "rebind";

    private static final Set<String> OPTIONS = Set.of(TRIGGER, "scope", "username", "batch", "operator", "confirm",
            "reason", "non-interactive");

    /** A claimed operator identifier: a staff id or login name, never free text (R-AUD-033). */
    private static final Pattern OPERATOR = Pattern.compile("[A-Za-z0-9._@-]{1,64}");

    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");

    /** Whether {@code arguments} ask for the runner: the {@value #TRIGGER} option, and nothing in the environment. */
    static boolean requested(ApplicationArguments arguments) {
        return arguments.containsOption(TRIGGER);
    }

    boolean dryRun() {
        return confirm == null;
    }

    /**
     * Reads and checks a runner invocation.
     *
     * @throws RecoveryRefusedException naming the first problem
     */
    static RecoveryInvocation parse(ApplicationArguments arguments) {
        if (!arguments.getNonOptionArgs().isEmpty()) {
            throw new RecoveryRefusedException("unexpected argument; every runner argument is an --option");
        }
        for (String name : arguments.getOptionNames()) {
            if (name.toLowerCase(Locale.ROOT).contains("password")) {
                throw new RecoveryRefusedException("--" + name + " is refused: no argument may carry a password, which"
                        + " other local processes can read (CWE-214). The single form reads it from a prompt, or from"
                        + " stdin with --non-interactive (ADR-073)");
            }
            if (name.startsWith("spring.main.")) {
                throw new RecoveryRefusedException("--" + name + " is refused: the runner sets how the application"
                        + " starts (ADR-072)");
            }
            if (!OPTIONS.contains(name) && !name.contains(".")) {
                throw new RecoveryRefusedException("unknown option --" + name);
            }
        }
        String scopeCode = value(arguments, "scope");
        if (scopeCode == null) {
            throw new RecoveryRefusedException("--scope is required: password, totp or both (ADR-072)");
        }
        RecoveryScope scope = RecoveryScope.of(scopeCode).orElseThrow(() ->
                new RecoveryRefusedException("--scope must be password, totp or both"));
        String username = value(arguments, "username");
        String batch = value(arguments, "batch");
        if ((username == null) == (batch == null)) {
            throw new RecoveryRefusedException("name the accounts with exactly one of --username and --batch");
        }
        String operator = value(arguments, "operator");
        if (operator == null || !OPERATOR.matcher(operator).matches()) {
            throw new RecoveryRefusedException("--operator is required: your staff identifier, 1 to 64 of"
                    + " A-Z a-z 0-9 . _ @ - (R-AUD-033)");
        }
        String confirm = value(arguments, "confirm");
        String reason = value(arguments, "reason");
        if (confirm != null && !DIGEST.matcher(confirm).matches()) {
            throw new RecoveryRefusedException("--confirm must be the 64-character digest a dry run printed");
        }
        if ((confirm == null) != (reason == null)) {
            throw new RecoveryRefusedException("--confirm and --reason go together: a dry run takes neither, and an"
                    + " apply needs both (ADR-074)");
        }
        if (reason != null && (reason.isBlank() || reason.length() > RecoveryRunContext.REASON_MAX_LENGTH)) {
            throw new RecoveryRefusedException("--reason must be 1 to " + RecoveryRunContext.REASON_MAX_LENGTH
                    + " characters");
        }
        boolean nonInteractive = arguments.containsOption("non-interactive");
        if (nonInteractive && !arguments.getOptionValues("non-interactive").isEmpty()) {
            throw new RecoveryRefusedException("--non-interactive takes no value");
        }
        return new RecoveryInvocation(scope, username, batch == null ? null : Path.of(batch), operator, confirm,
                reason, nonInteractive);
    }

    /** The option's single value, or {@code null} if it is absent. */
    private static @Nullable String value(ApplicationArguments arguments, String name) {
        List<String> values = arguments.getOptionValues(name);
        if (values == null) {
            return null;
        }
        if (values.size() != 1 || values.getFirst().isEmpty()) {
            throw new RecoveryRefusedException("--" + name + " takes exactly one value, given once");
        }
        return values.getFirst();
    }
}
