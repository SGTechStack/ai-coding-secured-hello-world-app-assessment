package sg.securedhello.build;

import static org.assertj.core.api.Assertions.fail;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Runs the strict {@code @Proves} traceability gate in {@code verify}, under Failsafe (ADR-068): every test-plan row
 * has a test, and every citation is a row. Paths come from the Maven properties {@code test-plan.path} and
 * {@code traceability.frontend.dir}.
 */
class TraceabilityGateIT {

    @Test
    void everyRowHasATestAndEveryCitationIsARow() {
        Citations citations = new Citations()
                .addJavaTests("sg.securedhello")
                .addFrontendTests(path("traceability.frontend.dir", "../frontend"));
        TraceabilityGate gate = new TraceabilityGate(
                TestPlanTable.readIds(path("test-plan.path", "../docs/test-plan/test-plan.md")), citations.byId());

        List<String> problems = gate.problems();
        if (!problems.isEmpty()) {
            fail("Traceability gate failed (ADR-068):%n%n%s", String.join(System.lineSeparator().repeat(2), problems));
        }
    }

    private static Path path(String property, String fallback) {
        return Path.of(System.getProperty(property, fallback)).toAbsolutePath().normalize();
    }
}
