package sg.securedhello.build;

import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Runs the {@code @Proves} traceability gate in {@code verify}, under Failsafe (ADR-068).
 *
 * <p>Paths come from the Maven properties {@code test-plan.path}, {@code traceability.ledger.path} and
 * {@code traceability.frontend.dir}. Each run writes the ledger entries that already have tests to
 * {@code target/traceability/ledger-proven.txt}, one per line, for reconciliation after a merge.
 */
class TraceabilityGateIT {

    @Test
    void everyRowHasATestOrALedgerEntryAndEveryCitationIsARow() throws IOException {
        Path ledgerPath = path("traceability.ledger.path", "../docs/test-plan/pending-ledger.txt");
        Citations citations = new Citations()
                .addJavaTests("sg.securedhello")
                .addFrontendTests(path("traceability.frontend.dir", "../frontend"));
        TraceabilityGate gate = new TraceabilityGate(
                TestPlanTable.readIds(path("test-plan.path", "../docs/test-plan/test-plan.md")),
                TraceabilityGate.readLedger(ledgerPath),
                citations.byId(),
                ledgerPath);

        Path report = Path.of("target", "traceability", "ledger-proven.txt");
        Files.createDirectories(report.getParent());
        Files.write(report, gate.ledgerProven());

        List<String> problems = gate.problems();
        if (!problems.isEmpty()) {
            fail("Traceability gate failed (ADR-068):%n%n%s", String.join(System.lineSeparator().repeat(2), problems));
        }
    }

    private static Path path(String property, String fallback) {
        return Path.of(System.getProperty(property, fallback)).toAbsolutePath().normalize();
    }
}
