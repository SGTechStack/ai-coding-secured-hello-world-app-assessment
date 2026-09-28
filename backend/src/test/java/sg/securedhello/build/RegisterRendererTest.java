package sg.securedhello.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The register's schema checks, both renderings, and drift detection, against a synthetic register (ADR-069). */
class RegisterRendererTest {

    private static final String HEADER = "| " + String.join(" | ", RegisterTable.COLUMNS) + " |";
    private static final String SEPARATOR = "| --- ".repeat(16) + "|";

    // ID, kind, requirement, level, decision, residual, responsibility, status, priority, sequence, acceptance
    private static final String OBLIGATION = row("R-CFG-001", "obligation", "ASVS 13.3.1; IM8 as-9", "L2; —",
            "Rotate the keys.", "Key exposure.", "deployer", "procedural", "required", "1", "A rotation record.");
    private static final String NOTE = row("R-AUTH-001", "note", "Std §5:509", "—", "No copy is kept.", "none",
            "none", "enforced", "", "", "T-AUTH-001 passes.");
    private static final String LIMITATION = row("R-OPS-001", "fidelity", "RFC 6238", "SHOULD", "Clock skew.",
            "Drift.", "none", "unmitigated", "", "L", "none possible.");

    @TempDir
    Path dir;

    @Test
    void complianceRendersEveryRowWithAnAnchorWhenSequenced() throws IOException {
        String compliance = RegisterRenderer.compliance(read(OBLIGATION, NOTE, LIMITATION), "handover.md");

        assertThat(compliance)
                .startsWith("# Deferral register (compliance rendering)\n")
                .contains("## Deployment assumption\n\n**No real deployment exists.**\n")
                .contains("| ID | requirement | level | verdict | kind | deviation | residual | handover |\n")
                .contains("| R-CFG-001 | ASVS 13.3.1; IM8 as-9 | L2; — | pass | obligation | Rotate the keys. "
                        + "| Key exposure. | [step 1](handover.md#r-cfg-001) |\n")
                .contains("| R-AUTH-001 | Std §5:509 | — | pass | note | No copy is kept. | none |  |\n")
                .contains("[limitation](handover.md#r-ops-001)");
    }

    @Test
    void handoverGroupsSequencedRowsInDeploymentOrder() throws IOException {
        String handover = RegisterRenderer.handover(read(LIMITATION, NOTE, OBLIGATION));

        assertThat(handover)
                .startsWith("# Operational handover\n")
                .contains("## Deployment assumption\n\n**No real deployment exists.**\n")
                .contains("| <a id=\"r-cfg-001\"></a>R-CFG-001 | Rotate the keys. | deployer | required | procedural "
                        + "| A rotation record. |\n")
                .doesNotContain("R-AUTH-001")
                .endsWith("none possible. |\n");
        assertThat(handover.indexOf("## 1. Keys")).isLessThan(handover.indexOf("## 2. Trusted-proxy"));
        assertThat(handover.indexOf("## 7. Recovery")).isLessThan(handover.indexOf("## L. Limitations"));
        assertThat(handover).contains("## 2. Trusted-proxy configuration\n\nNo rows.\n");
    }

    @Test
    void aHandEditedRenderingDriftsAndARegeneratedOnePasses() throws IOException {
        String regenerated = RegisterRenderer.handover(read(OBLIGATION, LIMITATION));
        Path committed = dir.resolve("handover.md");

        Files.writeString(committed, regenerated.replace("Rotate the keys.", "Rotate the keys eventually."));
        assertThat(RegisterRenderer.drift(committed, regenerated)).get().asString()
                .contains("differs from the regenerated rendering at line")
                .contains("committed:   | <a id=\"r-cfg-001\"></a>R-CFG-001 | Rotate the keys eventually.");

        Files.writeString(committed, regenerated.replace("\n", "\r\n"));
        assertThat(RegisterRenderer.drift(committed, regenerated)).isEmpty();

        assertThat(RegisterRenderer.drift(dir.resolve("absent.md"), regenerated)).get().asString()
                .contains("missing or unreadable");
    }

    @Test
    void schemaViolationsAreReportedPerRow() throws IOException {
        RegisterTable table = read(
                row("R-X-001", "note", "A; B", "PRD", "d", "r", "none", "enforced", "", "", "a"),
                row("R-X-002", "obligation", "A", "—", "d", "r", "deployer", "procedural", "required", "", "a"),
                row("R-X-003", "obligation", "A", "—", "d", "r", "shared", "procedural", "", "9", "a"),
                "| R-X-004 | too | few |");

        assertThat(table.problems()).containsExactly(
                "R-X-001: 1 level entries for 2 requirements; the schema needs one level per requirement, in order",
                "R-X-002: a deployer row needs a sequence",
                "R-X-003: priority must be empty exactly when responsibility is none",
                "R-X-003: sequence '9' is not one of 1-7 or L",
                "R-X-004: has 3 cells, not 16");
        assertThat(read(OBLIGATION, NOTE, LIMITATION).problems()).isEmpty();
    }

    @Test
    void failsWithoutTheTableOrTheDeploymentAssumption() throws IOException {
        Path noTable = Files.write(dir.resolve("a.md"), List.of("## Deployment assumption", "x"));
        Path noHeader = Files.write(dir.resolve("b.md"), List.of("## Table", HEADER, SEPARATOR, OBLIGATION));

        assertThatThrownBy(() -> RegisterTable.read(noTable)).hasMessageContaining("has no table");
        assertThatThrownBy(() -> RegisterTable.read(noHeader)).hasMessageContaining("Deployment assumption");
    }

    private RegisterTable read(String... rows) throws IOException {
        List<String> lines = new ArrayList<>(List.of("# Register", "", "## Deployment assumption", "",
                "**No real deployment exists.**", "", "## Table", "", HEADER, SEPARATOR));
        lines.addAll(List.of(rows));
        lines.add("");
        return RegisterTable.read(Files.write(dir.resolve("register.md"), lines));
    }

    private static String row(String id, String kind, String requirement, String level, String decision,
            String residual, String responsibility, String status, String priority, String sequence,
            String acceptance) {
        return "| " + String.join(" | ", id, "X", kind, requirement, level, "pass", "subject", decision, "why",
                residual, responsibility, status, priority, sequence, acceptance, "T-AUTH-001") + " |";
    }
}
