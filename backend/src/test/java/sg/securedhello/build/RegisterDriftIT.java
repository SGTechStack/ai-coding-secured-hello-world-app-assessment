package sg.securedhello.build;

import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import sg.securedhello.testsupport.Proves;

/**
 * The register drift gate (ADR-069): regenerates both renderings from {@code register.md} in {@code verify}, under
 * Failsafe, and fails when a committed rendering differs.
 *
 * <p>Paths come from the Maven properties {@code register.path}, {@code register.compliance.path} and
 * {@code register.handover.path}. With {@code -Dregister.regenerate=true} it writes the renderings instead of
 * comparing them.
 */
class RegisterDriftIT {

    @Test
    @Proves("T-BLD-006")
    void committedRenderingsMatchTheSourceTable() throws IOException {
        RegisterTable table = RegisterTable.read(path("register.path", "../docs/register/register.md"));
        Path compliance = path("register.compliance.path", "../docs/register/deferral-register.md");
        Path handover = path("register.handover.path", "../docs/register/handover.md");
        Map<Path, String> renderings = Map.of(
                compliance, RegisterRenderer.compliance(table, handover.getFileName().toString()),
                handover, RegisterRenderer.handover(table));

        List<String> problems = new ArrayList<>(table.problems());
        if (!problems.isEmpty()) {
            fail("register.md breaks the register schema (REJ-075):%n    %s",
                    String.join(System.lineSeparator() + "    ", problems));
        }
        if (Boolean.getBoolean("register.regenerate")) {
            for (Map.Entry<Path, String> rendering : renderings.entrySet()) {
                Files.writeString(rendering.getKey(), rendering.getValue());
            }
            return;
        }
        renderings.forEach((committed, regenerated) ->
                RegisterRenderer.drift(committed, regenerated).ifPresent(problems::add));
        if (!problems.isEmpty()) {
            fail("Register drift gate failed (ADR-069). Edit register.md, not a rendering, then regenerate with%n"
                    + "    mvn -f backend/pom.xml verify -Dregister.regenerate=true%n%n%s",
                    String.join(System.lineSeparator(), problems));
        }
    }

    private static Path path(String property, String fallback) {
        return Path.of(System.getProperty(property, fallback)).toAbsolutePath().normalize();
    }
}
