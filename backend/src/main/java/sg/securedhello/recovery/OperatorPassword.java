package sg.securedhello.recovery;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Where the single form's password comes from: never an argument (ADR-073). The mode is the explicit
 * {@code --non-interactive} flag, never whether {@code System.console()} is null (T-RUN-012; R-RUN-011):
 * <ul>
 *   <li>interactive: {@link Console#readPassword}, which does not echo. With no console it refuses rather than read
 *       whatever is piped in. On Java 22 or later {@code System.console()} is non-null even when the streams are
 *       redirected (JDK-8308591), so this refusal stops refusing; the digest bounds that to one execution, and the
 *       JDK upgrade is a rehearsal trigger (ADR-072);</li>
 *   <li>non-interactive: the first line of stdin. End of input refuses at once and never waits (T-RUN-003).</li>
 * </ul>
 * This class is the only main-code caller of {@code System.console()} (ArchUnit).
 */
final class OperatorPassword {

    private OperatorPassword() {
    }

    /**
     * The password the operator supplies for {@code username}.
     *
     * @throws RecoveryRefusedException if none is supplied
     */
    static String read(boolean nonInteractive, InputStream stdin, String username) {
        return nonInteractive ? fromStdin(stdin) : fromConsole(username);
    }

    private static String fromStdin(InputStream stdin) {
        String line;
        try {
            line = new BufferedReader(new InputStreamReader(stdin, StandardCharsets.UTF_8)).readLine();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (line == null || line.isEmpty()) {
            throw new RecoveryRefusedException("--non-interactive reads the new password as the first line of stdin,"
                    + " and stdin had none");
        }
        return line;
    }

    private static String fromConsole(String username) {
        Console console = System.console();
        if (console == null) {
            throw new RecoveryRefusedException("no terminal to prompt on. Run it in a terminal, or pass"
                    + " --non-interactive and supply the password as the first line of stdin");
        }
        char[] password = console.readPassword("New password for %s: ", username);
        if (password == null || password.length == 0) {
            throw new RecoveryRefusedException("no password was entered");
        }
        String copy = new String(password);
        Arrays.fill(password, '\0');
        return copy;
    }
}
