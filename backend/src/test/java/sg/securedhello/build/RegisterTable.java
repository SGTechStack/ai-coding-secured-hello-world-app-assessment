package sg.securedhello.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * The single source table behind the register's two renderings (ADR-069), read from {@code register.md}, with its
 * deployment-assumption header. The spec's register schema (REJ-075) governs it; {@link #problems()} checks it.
 *
 * @param header the body of the {@code ## Deployment assumption} section, rendered at the top of both documents
 * @param rows the table rows, in file order
 */
record RegisterTable(String header, List<Row> rows) {

    /** The sixteen columns, in order (spec, "Register and handover schema"). */
    static final List<String> COLUMNS = List.of("ID", "pillar", "kind", "requirement", "level", "verdict", "subject",
            "decision", "rationale", "residual", "responsibility", "status", "priority", "sequence", "acceptance",
            "refs");

    private static final String HEADER_HEADING = "## Deployment assumption";
    private static final Set<String> SEQUENCES = Set.of("1", "2", "3", "4", "5", "6", "7", "L");
    private static final String UNGRADED = "—";

    /** One register row; field names follow the schema's columns. */
    record Row(List<String> cells) {

        String id() {
            return cells.get(0);
        }

        String get(String column) {
            return cells.get(COLUMNS.indexOf(column));
        }
    }

    static RegisterTable read(Path path) {
        List<String> lines;
        try {
            lines = Files.readAllLines(path);
        } catch (IOException e) {
            throw new GateFailure("The register " + path + " is missing or unreadable: " + e);
        }
        int headerLine = IntStream.range(0, lines.size())
                .filter(i -> lines.get(i).startsWith("|") && TestPlanTable.cells(lines.get(i)).equals(COLUMNS))
                .findFirst()
                .orElseThrow(() -> new GateFailure("The register " + path + " has no table with the columns "
                        + COLUMNS));
        List<Row> rows = lines.subList(headerLine + 2, lines.size()).stream()
                .takeWhile(line -> line.startsWith("|"))
                .map(line -> new Row(TestPlanTable.cells(line)))
                .toList();
        return new RegisterTable(section(lines, path), rows);
    }

    private static String section(List<String> lines, Path path) {
        int start = lines.indexOf(HEADER_HEADING);
        if (start < 0) {
            throw new GateFailure("The register " + path + " has no '" + HEADER_HEADING + "' section (R-OPS-005).");
        }
        List<String> body = lines.subList(start + 1, lines.size()).stream()
                .takeWhile(line -> !line.startsWith("## "))
                .toList();
        return String.join("\n", body).strip();
    }

    /** Every schema violation, one message per row and rule; empty when the table conforms. */
    List<String> problems() {
        List<String> problems = new ArrayList<>();
        for (Row row : rows) {
            if (row.cells().size() != COLUMNS.size()) {
                problems.add(row.id() + ": has " + row.cells().size() + " cells, not " + COLUMNS.size());
                continue;
            }
            String responsibility = row.get("responsibility");
            String sequence = row.get("sequence");
            if (row.get("priority").isEmpty() != responsibility.equals("none")) {
                problems.add(row.id() + ": priority must be empty exactly when responsibility is none");
            }
            if (!sequence.isEmpty() && !SEQUENCES.contains(sequence)) {
                problems.add(row.id() + ": sequence '" + sequence + "' is not one of 1-7 or L");
            }
            if (sequence.isEmpty() && (responsibility.equals("deployer") || responsibility.equals("shared"))) {
                problems.add(row.id() + ": a " + responsibility + " row needs a sequence");
            }
            int requirements = row.get("requirement").split(";").length;
            int levels = row.get("level").split(";").length;
            if (!row.get("level").equals(UNGRADED) && levels != requirements) {
                problems.add(row.id() + ": " + levels + " level entries for " + requirements
                        + " requirements; the schema needs one level per requirement, in order");
            }
        }
        return problems;
    }
}
