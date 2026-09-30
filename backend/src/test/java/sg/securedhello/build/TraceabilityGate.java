package sg.securedhello.build;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The strict two-way {@code @Proves} gate (ADR-068): it fails when a test cites an ID that is not a row, and when a row
 * has no test. There is no pending ledger: every row in the test-plan table is proven by at least one test. A retired
 * ID is removed from the table (its reason stays in the plan's prose), so no test may cite it either.
 *
 * @param rows the test-plan row IDs
 * @param citations the cited IDs, each with where it was cited
 */
record TraceabilityGate(Set<String> rows, Map<String, Set<String>> citations) {

    /** Every disagreement, as a message that says exactly what to change; empty when the gate passes. */
    List<String> problems() {
        List<String> problems = new ArrayList<>();

        List<String> unknown = new TreeMap<>(citations).entrySet().stream()
                .filter(entry -> !rows.contains(entry.getKey()))
                .map(entry -> entry.getKey() + "  cited by " + String.join(", ", new TreeSet<>(entry.getValue())))
                .toList();
        section(problems, "Tests cite IDs that are not rows in the test plan; fix the test or add the row:", unknown);

        List<String> untested = rows.stream().filter(id -> !citations.containsKey(id)).toList();
        section(problems, "Rows with no test; write a test that cites each with @Proves, or in its Vitest or Playwright"
                + " test name:", untested);
        return problems;
    }

    private static void section(List<String> problems, String heading, List<String> ids) {
        if (!ids.isEmpty()) {
            problems.add(heading + System.lineSeparator() + "    " + String.join(System.lineSeparator() + "    ", ids));
        }
    }
}
