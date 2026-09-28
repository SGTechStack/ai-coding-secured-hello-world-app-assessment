package sg.securedhello.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads the row IDs of the canonical test-plan table (ADR-068).
 *
 * <p>The table is the only thing in its file that parses as rows: the first line starting with {@code |} is the
 * header, the next is the separator, and every later {@code |} line is a row whose first cell is its T-ID.
 */
final class TestPlanTable {

    /** The fixed column list (T-BLD-008). Changing it amends T-BLD-008 and this parser together. */
    static final List<String> HEADER = List.of(
            "ID", "pillar", "control", "assertion", "level", "context", "isolation", "clause", "rationale", "polarity");

    static final Pattern T_ID = Pattern.compile("T-[A-Z][A-Z0-9]*-\\d{3}");

    private static final Pattern SEPARATOR_CELL = Pattern.compile(":?-+:?");

    private TestPlanTable() {
    }

    /**
     * Returns the row IDs in table order.
     *
     * @throws GateFailure when the file is missing or unreadable (T-BLD-007), or has the wrong header, zero rows, a
     *         malformed ID or a duplicate ID (T-BLD-008)
     */
    static Set<String> readIds(Path path) {
        List<String> lines = readLines(path);
        List<String> tableLines = lines.stream().filter(line -> line.startsWith("|")).toList();
        if (tableLines.isEmpty() || !cells(tableLines.get(0)).equals(HEADER)) {
            throw new GateFailure("The test plan " + path + " has no table with the header " + HEADER
                    + " (T-BLD-008). Found: " + (tableLines.isEmpty() ? "no table" : tableLines.get(0)));
        }
        Set<String> ids = new LinkedHashSet<>();
        for (String row : tableLines.subList(1, tableLines.size())) {
            List<String> cells = cells(row);
            if (cells.stream().allMatch(cell -> SEPARATOR_CELL.matcher(cell).matches())) {
                continue;
            }
            String id = cells.get(0);
            if (!T_ID.matcher(id).matches()) {
                throw new GateFailure("The test plan " + path + " has a row with a malformed ID: " + row);
            }
            if (!ids.add(id)) {
                throw new GateFailure("The test plan " + path + " lists " + id + " more than once.");
            }
        }
        if (ids.isEmpty()) {
            throw new GateFailure("The test plan " + path + " parses to zero rows (T-BLD-008).");
        }
        return ids;
    }

    private static List<String> readLines(Path path) {
        if (!Files.isRegularFile(path)) {
            throw new GateFailure("The test plan " + path + " is missing (T-BLD-007). Check the Maven property "
                    + "test-plan.path.");
        }
        try {
            return Files.readAllLines(path);
        } catch (IOException e) {
            throw new GateFailure("The test plan " + path + " is unreadable (T-BLD-007): " + e);
        }
    }

    static List<String> cells(String tableLine) {
        String inner = tableLine.strip();
        inner = inner.substring(1, inner.endsWith("|") && inner.length() > 1 ? inner.length() - 1 : inner.length());
        return Arrays.stream(inner.split("\\|", -1)).map(String::strip).toList();
    }
}
