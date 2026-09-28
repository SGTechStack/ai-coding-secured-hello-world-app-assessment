package sg.securedhello.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import sg.securedhello.testsupport.Proves;

/** The traceability gate's decisions, against synthetic plans, ledgers and citations (ADR-068). */
class TraceabilityGateTest {

    private static final String HEADER =
            "| ID | pillar | control | assertion | level | context | isolation | clause | rationale | polarity |";
    private static final String SEPARATOR = "|---|---|---|---|---|---|---|---|---|---|";
    private static final Path LEDGER = Path.of("pending-ledger.txt");

    @TempDir
    Path dir;

    // --- the test-plan file (T-BLD-007, T-BLD-008) ---

    @Test
    void readsRowIdsInTableOrder() throws IOException {
        Path plan = plan("# Plan", "", "prose", HEADER, SEPARATOR, row("T-AUTH-002"), row("T-AUTH-001"), "", "end");

        assertThat(TestPlanTable.readIds(plan)).containsExactly("T-AUTH-002", "T-AUTH-001");
    }

    @Test
    @Proves("T-BLD-007")
    void failsWhenThePlanIsMissing() {
        assertThatThrownBy(() -> TestPlanTable.readIds(dir.resolve("absent.md")))
                .isInstanceOf(GateFailure.class)
                .hasMessageContaining("is missing (T-BLD-007)");
    }

    @Test
    @Proves("T-BLD-007")
    void failsWhenThePlanIsUnreadable() throws IOException {
        Path plan = dir.resolve("plan.md");
        Files.write(plan, new byte[] {'|', ' ', (byte) 0xC3, (byte) 0x28, '\n'});

        assertThatThrownBy(() -> TestPlanTable.readIds(plan))
                .isInstanceOf(GateFailure.class)
                .hasMessageContaining("is unreadable (T-BLD-007)");
    }

    @Test
    @Proves("T-BLD-008")
    void failsWhenThePlanParsesToZeroRows() throws IOException {
        Path plan = plan("# Plan", HEADER, SEPARATOR, "", "no rows");

        assertThatThrownBy(() -> TestPlanTable.readIds(plan))
                .isInstanceOf(GateFailure.class)
                .hasMessageContaining("zero rows (T-BLD-008)");
    }

    @Test
    @Proves("T-BLD-008")
    void failsWhenThePlanHasNoTable() throws IOException {
        Path plan = plan("# Plan", "", "only prose");

        assertThatThrownBy(() -> TestPlanTable.readIds(plan))
                .isInstanceOf(GateFailure.class)
                .hasMessageContaining("(T-BLD-008)");
    }

    @Test
    @Proves("T-BLD-008")
    void failsWhenTheHeaderIsReordered() throws IOException {
        Path plan = plan(HEADER.replace("| level | context |", "| context | level |"), SEPARATOR, row("T-AUTH-001"));

        assertThatThrownBy(() -> TestPlanTable.readIds(plan))
                .isInstanceOf(GateFailure.class)
                .hasMessageContaining("(T-BLD-008)");
    }

    @Test
    @Proves("T-BLD-008")
    void failsWhenAColumnIsMissing() throws IOException {
        Path plan = plan(HEADER.replace(" polarity |", ""), SEPARATOR, row("T-AUTH-001"));

        assertThatThrownBy(() -> TestPlanTable.readIds(plan))
                .isInstanceOf(GateFailure.class)
                .hasMessageContaining("(T-BLD-008)");
    }

    @Test
    void failsOnAMalformedOrDuplicateRowId() throws IOException {
        Path malformed = plan(HEADER, SEPARATOR, row("AUTH-001"));
        Path duplicate = plan(HEADER, SEPARATOR, row("T-AUTH-001"), row("T-AUTH-001"));

        assertThatThrownBy(() -> TestPlanTable.readIds(malformed)).hasMessageContaining("malformed ID");
        assertThatThrownBy(() -> TestPlanTable.readIds(duplicate)).hasMessageContaining("more than once");
    }

    // --- the two-way gate and the ledger ---

    @Test
    void passesWhenEveryRowHasATestOrALedgerEntry() {
        TraceabilityGate gate = gate(List.of("T-AUTH-002"), Map.of("T-AUTH-001", Set.of("A#a")));

        assertThat(gate.problems()).isEmpty();
        assertThat(gate.ledgerProven()).isEmpty();
    }

    @Test
    void failsWhenATestCitesAnUnknownId() {
        TraceabilityGate gate = gate(List.of("T-AUTH-002"),
                Map.of("T-AUTH-001", Set.of("A#a"), "T-AUTH-999", Set.of("B#b", "src/x.test.ts")));

        assertThat(gate.problems()).singleElement().asString()
                .contains("not rows in the test plan")
                .contains("T-AUTH-999  cited by B#b, src/x.test.ts");
    }

    @Test
    void failsWhenARowHasNoTestAndNoLedgerEntry() {
        TraceabilityGate gate = gate(List.of(), Map.of("T-AUTH-001", Set.of("A#a")));

        assertThat(gate.problems()).singleElement().asString()
                .contains("ADD these lines to pending-ledger.txt")
                .endsWith("    T-AUTH-002");
    }

    @Test
    void failsWhenALedgerEntryAlreadyHasATest() {
        TraceabilityGate gate = gate(List.of("T-AUTH-001", "T-AUTH-002"), Map.of("T-AUTH-001", Set.of("A#a")));

        assertThat(gate.ledgerProven()).containsExactly("T-AUTH-001");
        assertThat(gate.problems()).singleElement().asString()
                .contains("REMOVE these lines from pending-ledger.txt")
                .endsWith("    T-AUTH-001");
    }

    @Test
    void failsOnLedgerEntriesThatAreNotRowsOrAreRepeated() {
        TraceabilityGate gate = gate(List.of("T-AUTH-002", "T-AUTH-002", "T-GONE-001"),
                Map.of("T-AUTH-001", Set.of("A#a")));

        assertThat(gate.problems()).hasSize(2)
                .anySatisfy(problem -> assertThat(problem).contains("not test-plan rows").endsWith("T-GONE-001"))
                .anySatisfy(problem -> assertThat(problem).contains("more than once").endsWith("T-AUTH-002"));
    }

    @Test
    void readsTheLedgerIgnoringBlanksAndComments() throws IOException {
        Path ledger = Files.writeString(dir.resolve("ledger.txt"), "# pending\n\nT-AUTH-001\n  T-AUTH-002  \n");

        assertThat(TraceabilityGate.readLedger(ledger)).containsExactly("T-AUTH-001", "T-AUTH-002");
        assertThatThrownBy(() -> TraceabilityGate.readLedger(dir.resolve("absent.txt")))
                .isInstanceOf(GateFailure.class);
    }

    // --- citations ---

    @Test
    void readsFrontendIdsFromTestNamesOnly() {
        Citations citations = new Citations();
        citations.addFrontendSource("""
                // T-AUTH-003 in a comment is not a citation
                describe('suite T-SES-001', () => {
                  it("T-AUTH-001: first", () => {})
                  test.skip(`T-AUTH-002 and T-HDR-004: two ids`, async ({ page }) => {})
                  it('T-AUTH-004 isn\\'t escaped away', () => {})
                  expect(x).toBe('T-AUTH-005')
                })
                """, "src/a.test.tsx");

        assertThat(citations.byId()).containsOnlyKeys("T-SES-001", "T-AUTH-001", "T-AUTH-002", "T-HDR-004",
                "T-AUTH-004");
    }

    @Test
    void scansFrontendTestFilesAndSkipsDependencies() throws IOException {
        write("src/App.test.tsx", "it('T-HDR-006: x', () => {})");
        write("e2e/doc.spec.ts", "test('T-E2E-001: x', async () => {})");
        write("src/App.tsx", "it('T-AUTH-001: not a test file', () => {})");
        write("node_modules/pkg/a.test.ts", "it('T-AUTH-002: dependency', () => {})");

        Citations citations = new Citations().addFrontendTests(dir);

        assertThat(citations.byId()).containsOnlyKeys("T-HDR-006", "T-E2E-001");
        assertThat(citations.byId().get("T-E2E-001")).containsExactly("e2e/doc.spec.ts");
    }

    @Test
    void readsJavaProvesButNotTheFixturePlaceholders() {
        Citations citations = new Citations().addJavaTests("sg.securedhello");

        assertThat(citations.byId()).containsKeys("T-ARCH-001", "T-BLD-007");
        assertThat(citations.byId().get("T-ARCH-001"))
                .contains("sg.securedhello.architecture.ArchitectureTest#testsNeverSleep");
        assertThat(citations.byId().values()).flatMap(locations -> locations)
                .noneMatch(location -> location.contains(".fixtures."));
    }

    private TraceabilityGate gate(List<String> ledger, Map<String, Set<String>> citations) {
        return new TraceabilityGate(Set.of("T-AUTH-001", "T-AUTH-002"), ledger, citations, LEDGER);
    }

    private Path plan(String... lines) throws IOException {
        return Files.write(Files.createTempFile(dir, "plan", ".md"), List.of(lines));
    }

    private void write(String relative, String content) throws IOException {
        Path file = dir.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static String row(String id) {
        return "| " + id + " | AUTH | c | a | U | none | none | ADR-068 |  | neg |";
    }
}
