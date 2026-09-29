package sg.securedhello.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The prohibited-configuration rules for the resolved {@code spring.datasource.url} (ADR-051; ADR-072; spec, Data):
 * <ul>
 *   <li>unset, or H2 in memory (any of its in-memory file systems) or not in file mode, in every posture; and
 *       under {@code dev}, anything but an H2 file;</li>
 *   <li>{@code FILE_LOCK=NO}, which drops the single-writer lock, in every posture;</li>
 *   <li>{@code AUTO_SERVER} set to anything H2 does not read as false, which opens a database server, in every
 *       posture;</li>
 *   <li>{@code INIT}, whose SQL runs after connecting and could undo any of these, and any backslash in the
 *       settings, which H2 unescapes so that an escaped {@code ;} in a value runs as further SQL, in every
 *       posture;</li>
 *   <li>outside {@code dev}, an H2 URL that does not pin {@code LOCK_TIMEOUT=1000}. Under {@code dev} the test harness
 *       shortens it for the one test that waits on a row lock (ADR-066).</li>
 * </ul>
 * Settings are read case-insensitively with surrounding whitespace ignored; a repeated setting is judged on every
 * value.
 * Messages never echo the URL, which may carry credentials.
 */
final class DatasourceUrlRules {

    static final String PROPERTY = "spring.datasource.url";

    static final String REQUIRED_LOCK_TIMEOUT = "1000";

    private static final String H2_PREFIX = "jdbc:h2:";

    /**
     * H2's in-memory databases and file systems, anywhere in the location: {@code mem:}, {@code memFS:},
     * {@code memLZF:}, {@code nioMemFS:} and {@code nioMemLZF:}, also behind {@code file:} or a TCP server path.
     */
    private static final Pattern IN_MEMORY = Pattern.compile("(^|[/:])(nio)?mem(fs|lzf)?:");

    /** The values H2 2.x reads as false for a boolean setting ({@code Utils.parseBoolean}), upper-cased. */
    private static final Set<String> H2_FALSE = Set.of("FALSE", "F", "NO", "N", "0");

    private DatasourceUrlRules() {
    }

    /** Every rule {@code url} breaks; empty when it is allowed. */
    static List<String> violations(String url, boolean dev) {
        if (url == null || url.isBlank()) {
            return List.of(PROPERTY + " is unset, so an in-memory database would be used (ADR-051)");
        }
        String lower = url.trim().toLowerCase(Locale.ROOT);
        boolean h2 = lower.startsWith(H2_PREFIX);
        if (h2 && IN_MEMORY.matcher(lower.substring(H2_PREFIX.length())).find()) {
            return List.of(PROPERTY + " names an in-memory H2 database (ADR-051)");
        }
        List<String> violations = new ArrayList<>();
        if ((dev || h2) && !lower.startsWith(H2_PREFIX + "file:")) {
            violations.add(PROPERTY + " is not an H2 file database (ADR-051; spec, Data)");
        }
        String settingsText = settingsText(url);
        Map<String, List<String>> settings = settings(settingsText);
        if (settings.getOrDefault("FILE_LOCK", List.of()).contains("NO")) {
            violations.add(PROPERTY + " sets FILE_LOCK=NO, which drops the single-writer lock (ADR-072)");
        }
        if (!H2_FALSE.containsAll(settings.getOrDefault("AUTO_SERVER", List.of()))) {
            violations.add(PROPERTY + " enables AUTO_SERVER, which opens a database server (ADR-072)");
        }
        if (settingsText.indexOf('\\') >= 0) {
            violations.add(PROPERTY + " has a backslash in its settings; H2 would unescape it, and an escaped ; runs "
                    + "as SQL after connecting (ADR-072)");
        }
        if (settings.containsKey("INIT")) {
            violations.add(PROPERTY + " sets INIT, whose SQL could undo these settings after connecting (ADR-072)");
        }
        if (!dev && h2 && !pinsLockTimeout(settings.getOrDefault("LOCK_TIMEOUT", List.of()))) {
            violations.add(PROPERTY + " does not pin LOCK_TIMEOUT=" + REQUIRED_LOCK_TIMEOUT + " (spec, Data)");
        }
        return violations;
    }

    private static boolean pinsLockTimeout(List<String> values) {
        return !values.isEmpty() && values.stream().allMatch(REQUIRED_LOCK_TIMEOUT::equals);
    }

    /**
     * The URL's {@code ;KEY=VALUE} settings, keys and values trimmed and upper-cased, every value kept. As in H2's
     * {@code ConnectionInfo.readSettingsFromURL}, the database name ends at the first {@code ;} whatever precedes it.
     * A backslash in the settings is refused on its own, so a plain split reads them as H2 would.
     */
    private static Map<String, List<String>> settings(String settingsText) {
        Map<String, List<String>> settings = new HashMap<>();
        for (String part : settingsText.split(";")) {
            int equals = part.indexOf('=');
            if (equals >= 0) {
                settings.computeIfAbsent(part.substring(0, equals).trim().toUpperCase(Locale.ROOT),
                        key -> new ArrayList<>()).add(part.substring(equals + 1).trim().toUpperCase(Locale.ROOT));
            }
        }
        return settings;
    }

    /** Everything after the database name, or empty when the URL has no settings. */
    private static String settingsText(String url) {
        int nameEnd = url.indexOf(';');
        return nameEnd < 0 ? "" : url.substring(nameEnd + 1);
    }
}
