package sg.securedhello.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import sg.securedhello.build.fixtures.AbstractCitationFixtureTest;
import sg.securedhello.build.fixtures.CitationFixtures;
import sg.securedhello.build.fixtures.DisabledCitationFixtureTest;
import sg.securedhello.testsupport.Proves;

/** The traceability gate's decisions, against synthetic plans and citations (ADR-068). */
class TraceabilityGateTest {

    private static final String HEADER =
            "| ID | pillar | control | assertion | level | context | isolation | clause | rationale | polarity |";
    private static final String SEPARATOR = "|---|---|---|---|---|---|---|---|---|---|";

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

    // --- the strict two-way gate ---

    @Test
    void passesWhenEveryRowHasATestAndEveryCitationIsARow() {
        TraceabilityGate gate = gate(Map.of("T-AUTH-001", Set.of("A#a"), "T-AUTH-002", Set.of("src/x.test.ts")));

        assertThat(gate.problems()).isEmpty();
    }

    @Test
    void failsWhenATestCitesAnUnknownId() {
        TraceabilityGate gate = gate(Map.of("T-AUTH-001", Set.of("A#a"), "T-AUTH-002", Set.of("A#b"),
                "T-AUTH-999", Set.of("B#b", "src/x.test.ts")));

        assertThat(gate.problems()).singleElement().asString()
                .contains("not rows in the test plan")
                .contains("T-AUTH-999  cited by B#b, src/x.test.ts");
    }

    @Test
    void failsWhenARowHasNoTest() {
        TraceabilityGate gate = gate(Map.of("T-AUTH-001", Set.of("A#a")));

        assertThat(gate.problems()).singleElement().asString()
                .contains("Rows with no test")
                .endsWith("    T-AUTH-002");
    }

    // --- citations ---

    @Test
    void readsFrontendIdsFromTestNamesOnly() {
        Citations citations = new Citations();
        citations.addFrontendSource("""
                // T-AUTH-003 in a comment is not a citation
                describe('suite T-SES-001', () => {
                  it("T-AUTH-001: first", () => {})
                  test(`T-AUTH-002 and T-HDR-004: two ids`, async ({ page }) => {})
                  it('T-AUTH-004 isn\\'t escaped away', () => {})
                  expect(x).toBe('T-AUTH-005')
                  expect(/T-/.test('T-AUTH-006')).toBe(true)
                  submit('T-AUTH-007')
                })
                """, "src/a.test.tsx");

        assertThat(citations.byId()).containsOnlyKeys("T-AUTH-001", "T-AUTH-002", "T-HDR-004", "T-AUTH-004");
    }

    @Test
    void citesOnlyFrontendTestsThatRun() {
        Citations citations = new Citations();
        citations.addFrontendSource("""
                describe('T-SES-001: a suite name is not a test', () => {
                  it.only('T-AUTH-001: only runs', () => {})
                  it.concurrent('T-AUTH-002: concurrent runs', async () => {})
                  it.skip('T-SES-002: skipped', () => {})
                  it.todo('T-SES-003: todo')
                  it('T-SES-004: a todo with no callback')
                  test.fixme('T-SES-005: fixme', async () => {})
                  test.fail('T-SES-006: expected to fail', async () => {})
                  test.describe('T-SES-007: a Playwright suite', () => {})
                  test.step('T-SES-008: a step', async () => {})
                })
                describe.skip('skipped suite', () => {
                  it('T-SES-009: inside a skipped suite (with "a ) in a string")', () => { f(')') })
                  // a ) in a comment
                  it('T-SES-010: still inside', () => {})
                })
                test.describe.fixme('fixme suite', () => { test('T-SES-011: inside', async () => {}) })
                it('T-AUTH-004: after the skipped suites', () => {})
                // it('T-SES-013: commented out', () => {})
                /* it('T-SES-014: in a block comment', () => {})
                   test('T-SES-015: still in it', async () => {}) */
                it('T-AUTH-006: after a // and a /* inside strings', () => { f('http://x', '/* y') })
                it('T-SES-016: skipped by options', { skip: true }, () => {})
                it('T-AUTH-007: options that do not skip', { timeout: 5 }, () => {})
                describe('suite skipped by options', { todo: true }, () => { it('T-SES-017: inside', () => {}) })
                describe.skip.each([1, 2])('skipped table suite %s', (n) => { it('T-SES-018: inside', () => {}) })
                it.each([[1, 2], [3, f(4)]])('T-AUTH-005 on %s: a table-driven test runs', (a, b) => {})
                test.skip.each([1])('T-SES-012: a skipped table', () => {})
                it.each([1])
                """, "src/a.test.tsx");

        assertThat(citations.byId()).containsOnlyKeys("T-AUTH-001", "T-AUTH-002", "T-AUTH-004", "T-AUTH-005",
                "T-AUTH-006", "T-AUTH-007");
    }

    @Test
    void aFileWithAnUnconditionalInBodySkipCitesNothing() {
        Citations citations = new Citations();
        citations.addFrontendSource("""
                test.describe('suite', () => {
                  test.skip()
                  test('T-SES-019: skipped with its suite', async () => {})
                })
                """, "e2e/a.spec.ts");
        citations.addFrontendSource("""
                test('T-AUTH-008: a conditional skip still runs', async ({ browserName }) => {
                  test.skip(browserName === 'webkit', 'not on webkit')
                })
                """, "e2e/b.spec.ts");

        assertThat(citations.byId()).containsOnlyKeys("T-AUTH-008");
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

    @Test
    void citesNoJavaTestThatNeverRuns() {
        Citations citations = new Citations().addJavaTests("sg.securedhello.build.fixtures");

        assertThat(citations.byId()).isEmpty();
    }

    @Test
    void aClassRunsOnlyWhenConcreteEnabledAndNamedForARunner() {
        JavaClasses classes = new ClassFileImporter().importClasses(TraceabilityGateTest.class,
                AbstractCitationFixtureTest.class, DisabledCitationFixtureTest.class, CitationFixtures.class,
                CitationFixtures.InsideAClassThatNeverRuns.class);

        assertThat(Citations.isRun(classes.get(TraceabilityGateTest.class))).isTrue();
        assertThat(Citations.isRun(classes.get(AbstractCitationFixtureTest.class))).as("abstract").isFalse();
        assertThat(Citations.isRun(classes.get(DisabledCitationFixtureTest.class))).as("disabled").isFalse();
        assertThat(Citations.isRun(classes.get(CitationFixtures.class))).as("no runner include").isFalse();
        assertThat(Citations.isRun(classes.get(CitationFixtures.InsideAClassThatNeverRuns.class)))
                .as("nested in a class that never runs").isFalse();
    }

    @Test
    void onlyEnabledJunitTestMethodsAreTests() {
        JavaClass fixtures = new ClassFileImporter().importClass(CitationFixtures.class);

        assertThat(Citations.testMethods(fixtures)).extracting(JavaMethod::getName)
                .containsExactlyInAnyOrder("aTest", "aParameterizedTest");
    }

    private static TraceabilityGate gate(Map<String, Set<String>> citations) {
        return new TraceabilityGate(Set.of("T-AUTH-001", "T-AUTH-002"), citations);
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
