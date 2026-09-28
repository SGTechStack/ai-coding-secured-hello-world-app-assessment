package sg.securedhello.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import sg.securedhello.build.RegisterTable.Row;

/**
 * Renders the register's source table as the compliance rendering (the deferral register) and the operational
 * rendering (the handover document), per the spec's "Register and handover schema" (ADR-069; REJ-075).
 *
 * <p>Output uses {@code \n} line endings and depends only on the table, so a regeneration is byte-stable.
 */
final class RegisterRenderer {

    /** The operational groups, in deployment order: steps 1–7, then {@code L}. */
    private static final Map<String, String> STEPS = Map.of(
            "1", "1. Keys and generation commands",
            "2", "2. Trusted-proxy configuration",
            "3", "3. Time synchronisation",
            "4", "4. Headers and origins",
            "5", "5. Mail transport",
            "6", "6. Log forwarding and retention",
            "7", "7. Recovery rehearsal",
            "L", "L. Limitations: what nobody can currently prove");

    private static final List<String> STEP_ORDER = List.of("1", "2", "3", "4", "5", "6", "7", "L");

    private static final String NOTICE = "<!-- Generated from register.md by the register drift gate (ADR-069; "
            + "T-BLD-006). Do not edit: amend register.md by ID, then regenerate. -->";

    private RegisterRenderer() {
    }

    /** Every row, with an anchor into {@code handoverFile} when the row has a sequence. */
    static String compliance(RegisterTable table, String handoverFile) {
        StringBuilder out = preamble("Deferral register (compliance rendering)", table);
        out.append("## Register\n\n");
        tableHeader(out, "ID", "requirement", "level", "verdict", "kind", "deviation", "residual", "handover");
        for (Row row : table.rows()) {
            String sequence = row.get("sequence");
            String anchor = sequence.isEmpty() ? ""
                    : "[" + (sequence.equals("L") ? "limitation" : "step " + sequence) + "](" + handoverFile + "#"
                            + anchorId(row) + ")";
            tableRow(out, row.id(), row.get("requirement"), row.get("level"), row.get("verdict"), row.get("kind"),
                    row.get("decision"), row.get("residual"), anchor);
        }
        return out.toString();
    }

    /** The rows with a sequence, grouped by deployment step 1–7 then {@code L}. */
    static String handover(RegisterTable table) {
        StringBuilder out = preamble("Operational handover", table);
        for (String step : STEP_ORDER) {
            List<Row> rows = table.rows().stream().filter(row -> row.get("sequence").equals(step)).toList();
            out.append("## ").append(STEPS.get(step)).append("\n\n");
            if (rows.isEmpty()) {
                out.append("No rows.\n\n");
                continue;
            }
            tableHeader(out, "ID", "what to do", "responsibility", "priority", "status", "how to prove it");
            for (Row row : rows) {
                tableRow(out, "<a id=\"" + anchorId(row) + "\"></a>" + row.id(), row.get("decision"),
                        row.get("responsibility"), row.get("priority"), row.get("status"), row.get("acceptance"));
            }
            out.append('\n');
        }
        return out.toString().stripTrailing() + "\n";
    }

    /**
     * Compares a committed rendering with its regenerated text, ignoring only CRLF versus LF.
     *
     * @return empty when they match; otherwise where they first differ
     */
    static Optional<String> drift(Path committed, String regenerated) {
        String actual;
        try {
            actual = Files.readString(committed).replace("\r\n", "\n");
        } catch (IOException e) {
            return Optional.of(committed + " is missing or unreadable: " + e);
        }
        if (actual.equals(regenerated)) {
            return Optional.empty();
        }
        String[] expectedLines = regenerated.split("\n", -1);
        String[] actualLines = actual.split("\n", -1);
        int line = 0;
        while (line < expectedLines.length && line < actualLines.length
                && expectedLines[line].equals(actualLines[line])) {
            line++;
        }
        return Optional.of(committed + " differs from the regenerated rendering at line " + (line + 1)
                + ":\n    committed:   " + lineOrEnd(actualLines, line)
                + "\n    regenerated: " + lineOrEnd(expectedLines, line));
    }

    private static String lineOrEnd(String[] lines, int index) {
        return index < lines.length ? lines[index] : "<end of file>";
    }

    private static StringBuilder preamble(String title, RegisterTable table) {
        return new StringBuilder()
                .append("# ").append(title).append("\n\n")
                .append(NOTICE).append("\n\n")
                .append("## Deployment assumption\n\n")
                .append(table.header()).append("\n\n");
    }

    private static String anchorId(Row row) {
        return row.id().toLowerCase(Locale.ROOT);
    }

    private static void tableHeader(StringBuilder out, String... columns) {
        tableRow(out, columns);
        tableRow(out, Collections.nCopies(columns.length, "---").toArray(String[]::new));
    }

    private static void tableRow(StringBuilder out, String... cells) {
        out.append("| ").append(String.join(" | ", cells)).append(" |\n");
    }
}
