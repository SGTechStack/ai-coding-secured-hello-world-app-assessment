package sg.securedhello.build;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the names of the Vitest and Playwright tests that run from a test file's source text, so the traceability
 * gate needs no Node runtime (ADR-068).
 *
 * <p>A test runs when it is an {@code it} or {@code test} call with a callback, with no modifier other than
 * {@code only}, {@code concurrent} or {@code sequential} (a table form, {@code it.each(table)('...', ...)}, included),
 * with no {@code { skip: true }} or {@code { todo: true }} options, outside comments and outside any skipped suite
 * ({@code describe.skip}, {@code .todo}, {@code .fixme}, their table forms, or a suite with skipping options). Suite
 * names, {@code skip}, {@code todo}, {@code fixme}, {@code fail} and {@code step} calls, and {@code it('...')} with no
 * callback (a Vitest todo) name no test. A file with an unconditional in-body {@code test.skip()} or
 * {@code test.fixme()}, which skips its whole enclosing suite in Playwright, names no test at all.
 *
 * <p>Known limits that fail open (a test that never runs can still be cited; none occurs in the frontend today): an
 * unpaired quote in a regex literal or JSX text ({@code /can't/}) is read as a string opening, which can hide a later
 * comment marker or end a skipped suite early, as can a regex literal holding a parenthesis or brace; a template
 * literal with a nested backtick; conditional skips ({@code skipIf}, {@code runIf}, {@code test.skip(condition)},
 * {@code test.skip(true)}) count as running, like JUnit's {@code @DisabledIf}; in-test {@code ctx.skip()},
 * third-argument options ({@code it('..', fn, { skip: true })}) and {@code xdescribe} are not recognised. Limits that
 * fail closed (a citation goes missing and the gate fails loudly): the tagged-template {@code it.each`...`} table.
 */
final class FrontendTestNames {

    /** The start of a test call, its modifiers and its opening parenthesis: {@code it(}, {@code test.only.each(}. */
    private static final Pattern TEST_CALL = Pattern.compile("(?<![.\\w])(?:it|test)((?:\\.\\w+)*)\\(");

    /** A test's literal name followed by a callback, as in {@code ('...', () => ...)}. */
    private static final Pattern TEST_NAME = Pattern.compile("\\s*(['\"`])((?:\\\\.|(?!\\1).)*)\\1\\s*,",
            Pattern.DOTALL);

    /** A table-driven test's modifier: its name comes in a second call, {@code it.each(table)('...', ...)}. */
    private static final Pattern TABLE_MODIFIER = Pattern.compile("\\.(?:each|for)$");

    /** The modifiers of a test that runs; anything else ({@code skip}, {@code todo}, {@code describe}...) runs none. */
    private static final Pattern RUNNING_MODIFIERS = Pattern.compile("(?:\\.(?:only|concurrent|sequential))*");

    /** The start of a suite call, its modifiers and its opening parenthesis: {@code describe.skip.each(}. */
    private static final Pattern SUITE_CALL = Pattern.compile("(?<![.\\w])(?:test\\.)?describe((?:\\.\\w+)*)\\(");

    /** A modifier that skips a suite. */
    private static final Pattern SKIPPING_MODIFIER = Pattern.compile("\\.(?:skip|todo|fixme)\\b");

    /** An options object that skips: {@code { skip: true }}, {@code { todo: true }}. */
    private static final Pattern SKIPPING_OPTION = Pattern.compile("\\b(?:skip|todo)\\s*:\\s*true\\b");

    /** An unconditional in-body skip, which in Playwright skips the whole enclosing suite. */
    private static final Pattern IN_BODY_SKIP = Pattern.compile("(?<![.\\w])test\\.(?:skip|fixme)\\(\\s*\\)");

    private FrontendTestNames() {
    }

    /** The literal names of the tests in {@code source} that run, in source order. */
    static List<String> runningTestNames(String source) {
        String running = withoutSkippedSuites(withoutComments(source));
        List<String> names = new ArrayList<>();
        if (IN_BODY_SKIP.matcher(running).find()) {
            return names;
        }
        Matcher call = TEST_CALL.matcher(running);
        while (call.find()) {
            nameStart(running, call).ifPresent(start -> {
                Matcher name = TEST_NAME.matcher(running).region(start, running.length());
                if (name.lookingAt() && !skippingOptions(running, name.end())) {
                    names.add(name.group(2));
                }
            });
        }
        return names;
    }

    /** Where the name of {@code call} begins, or empty when the call is not a test that runs. */
    private static OptionalInt nameStart(String source, Matcher call) {
        Matcher table = TABLE_MODIFIER.matcher(call.group(1));
        boolean tableForm = table.find();
        String modifiers = tableForm ? call.group(1).substring(0, table.start()) : call.group(1);
        if (!RUNNING_MODIFIERS.matcher(modifiers).matches()) {
            return OptionalInt.empty();
        }
        if (!tableForm) {
            return OptionalInt.of(call.end());
        }
        int afterTable = closingParenthesis(source, call.end() - 1);
        return source.startsWith("(", afterTable) ? OptionalInt.of(afterTable + 1) : OptionalInt.empty();
    }

    /** {@code source} with every {@code //} and {@code /* *\/} comment blanked out, strings left intact. */
    private static String withoutComments(String source) {
        StringBuilder kept = new StringBuilder(source);
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '\'' || c == '"' || c == '`') {
                i = endOfString(source, i);
            } else if (source.startsWith("//", i)) {
                i = blank(kept, i, indexOrEnd(source, "\n", i)) - 1;
            } else if (source.startsWith("/*", i)) {
                i = blank(kept, i, Math.min(indexOrEnd(source, "*/", i + 2) + 2, source.length())) - 1;
            }
        }
        return kept.toString();
    }

    /** Whether the options object that may follow a test or suite name at {@code at} skips it. */
    private static boolean skippingOptions(String source, int at) {
        int open = at;
        while (open < source.length() && Character.isWhitespace(source.charAt(open))) {
            open++;
        }
        return source.startsWith("{", open)
                && SKIPPING_OPTION.matcher(source.substring(open, closing(source, open, '{', '}'))).find();
    }

    /**
     * {@code source} with the call of every skipped suite blanked out: a skipping modifier ({@code describe.skip(...)},
     * and its table form {@code describe.skip.each(table)(...)}) or skipping options after the suite name.
     */
    private static String withoutSkippedSuites(String source) {
        StringBuilder kept = new StringBuilder(source);
        Matcher suite = SUITE_CALL.matcher(source);
        int from = 0;
        while (suite.find(from)) {
            String modifiers = suite.group(1);
            int arguments = suite.end();
            int end = closingParenthesis(source, suite.end() - 1);
            if (TABLE_MODIFIER.matcher(modifiers).find() && source.startsWith("(", end)) {
                arguments = end + 1;
                end = closingParenthesis(source, end);
            }
            Matcher name = TEST_NAME.matcher(source).region(arguments, source.length());
            boolean skipped = SKIPPING_MODIFIER.matcher(modifiers).find()
                    || name.lookingAt() && skippingOptions(source, name.end());
            from = skipped ? blank(kept, suite.start(), end) : suite.end();
        }
        return kept.toString();
    }

    /** Blanks {@code [from, to)} except line breaks, and returns {@code to}. */
    private static int blank(StringBuilder text, int from, int to) {
        for (int i = from; i < to; i++) {
            if (text.charAt(i) != '\n') {
                text.setCharAt(i, ' ');
            }
        }
        return to;
    }

    private static int closingParenthesis(String source, int open) {
        return closing(source, open, '(', ')');
    }

    /** The index after the {@code close} that balances the {@code opener} at {@code open}, skipping strings. */
    private static int closing(String source, int open, char opener, char close) {
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '\'' || c == '"' || c == '`') {
                i = endOfString(source, i);
            } else if (c == opener) {
                depth++;
            } else if (c == close && --depth == 0) {
                return i + 1;
            }
        }
        return source.length();
    }

    private static int endOfString(String source, int start) {
        char quote = source.charAt(start);
        for (int i = start + 1; i < source.length(); i++) {
            if (source.charAt(i) == '\\') {
                i++;
            } else if (source.charAt(i) == quote) {
                return i;
            }
        }
        return source.length();
    }

    private static int indexOrEnd(String source, String token, int from) {
        int index = source.indexOf(token, from);
        return index < 0 ? source.length() : index;
    }
}
