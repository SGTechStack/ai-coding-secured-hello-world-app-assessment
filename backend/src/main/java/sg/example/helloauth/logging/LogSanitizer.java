package sg.example.helloauth.logging;

import java.util.regex.Pattern;

/** Makes user input safe to put in a log field. */
public final class LogSanitizer {

    /** ASCII control characters (CR and LF among them) and the Unicode line and paragraph breaks. */
    private static final Pattern LINE_BREAKS_AND_CONTROLS = Pattern.compile("[\\p{Cntrl}\\u0085\\u2028\\u2029]");

    private LogSanitizer() {
    }

    /** Strips line breaks and other control characters, so the input can't start a forged log line. */
    public static String strip(String input) {
        return input == null ? null : LINE_BREAKS_AND_CONTROLS.matcher(input).replaceAll("");
    }
}
