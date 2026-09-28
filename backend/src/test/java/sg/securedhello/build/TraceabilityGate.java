package sg.securedhello.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The two-way {@code @Proves} gate (ADR-068), with the pending ledger that lets it land before the tests do.
 *
 * <p>The gate fails when a test cites an ID that is not a row, when a row has no test and is not on the ledger, and
 * when a ledger entry already has a test or is not a row. So the ledger can only shrink. Ticket 28 deletes it.
 *
 * @param rows the test-plan row IDs
 * @param ledger the pending-ledger entries, in file order
 * @param citations the cited IDs, each with where it was cited
 * @param ledgerPath the ledger's path, named in the failure message
 */
record TraceabilityGate(Set<String> rows, List<String> ledger, Map<String, Set<String>> citations, Path ledgerPath) {

    /** Reads the ledger: one T-ID per line; blank lines and {@code #} comments are ignored. */
    static List<String> readLedger(Path path) {
        try {
            return Files.readAllLines(path).stream()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .toList();
        } catch (IOException e) {
            throw new GateFailure("The pending ledger " + path + " is missing or unreadable: " + e
                    + ". Check the Maven property traceability.ledger.path.");
        }
    }

    /** Ledger entries whose rows now have tests. The orchestrator removes these after each merge. */
    Set<String> ledgerProven() {
        Set<String> proven = new TreeSet<>(ledger);
        proven.retainAll(citations.keySet());
        return proven;
    }

    /** Every disagreement, as a message that says exactly what to change; empty when the gate passes. */
    List<String> problems() {
        List<String> problems = new ArrayList<>();

        List<String> unknown = new TreeMap<>(citations).entrySet().stream()
                .filter(entry -> !rows.contains(entry.getKey()))
                .map(entry -> entry.getKey() + "  cited by " + String.join(", ", new TreeSet<>(entry.getValue())))
                .toList();
        section(problems, "Tests cite IDs that are not rows in the test plan; fix the test or add the row:", unknown);

        Set<String> ledgerSet = new HashSet<>(ledger);
        List<String> untested = rows.stream()
                .filter(id -> !citations.containsKey(id) && !ledgerSet.contains(id))
                .toList();
        section(problems, "Rows with no test that are not on the pending ledger; ADD these lines to " + ledgerPath
                + " or write their tests:", untested);

        section(problems, "Ledger entries whose rows now have tests; REMOVE these lines from " + ledgerPath + ":",
                List.copyOf(ledgerProven()));

        List<String> notRows = ledger.stream().filter(id -> !rows.contains(id)).distinct().toList();
        section(problems, "Ledger entries that are not test-plan rows; REMOVE these lines from " + ledgerPath + ":",
                notRows);

        Set<String> seen = new HashSet<>();
        List<String> duplicates = ledger.stream().filter(id -> !seen.add(id)).distinct().toList();
        section(problems, "Ledger entries listed more than once; keep one line each in " + ledgerPath + ":",
                duplicates);
        return problems;
    }

    private static void section(List<String> problems, String heading, List<String> ids) {
        if (!ids.isEmpty()) {
            problems.add(heading + System.lineSeparator() + "    " + String.join(System.lineSeparator() + "    ", ids));
        }
    }
}
