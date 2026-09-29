package sg.securedhello.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import sg.securedhello.config.ResetLinkLoggerGuard;
import sg.securedhello.testsupport.EcsJson;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.RestartHarness;
import sg.securedhello.testsupport.TestSecrets;

/** The lifecycle rows of a real startup and shutdown: row 43 (LOG §5:349; ADR-054; ADR-057) and row 44. */
@ExtendWith(OutputCaptureExtension.class)
class LifecycleRowsTest {

    @Test
    @Proves({"T-AUD-003", "T-CFG-037"})
    void theStartupRowCarriesTheServiceHostProfilesPrefixLengthAndFingerprintsButNoSecret(CapturedOutput output) {
        RestartHarness.Boot boot = RestartHarness.boot(builder -> builder.profiles("dev"),
                "--app.security.client-ip.ipv6-prefix-length=56");

        assertThat(boot.failure()).isNull();
        Map<String, Object> startup = onlyRow(output, "application-startup");
        assertThat(startup).containsEntry("log.logger", "audit")
                .containsEntry("event.type", List.of("start"))
                .containsEntry("event.outcome", "success")
                .containsEntry("service.name", "secured-hello-world")
                .containsEntry("labels.active_profiles", List.of("dev"))
                .containsEntry("labels.ipv6_prefix_length", 56)
                .containsKeys("service.version", "host.name", "host.ip")
                // The dev profile enables the dev-only link logger (ADR-057), and the row records it.
                .containsEntry("labels.audit_loggers", List.of("audit=INFO",
                        ResetLinkLoggerGuard.LOGGER_NAME + "=DEBUG"));
        assertThat((List<?>) startup.get("labels.key_fingerprints")).hasSize(3).allSatisfy(fingerprint ->
                assertThat(fingerprint.toString()).matches("app\\.[a-z.-]+\\.key=[0-9a-f]{8}"));
        assertThat(startup.toString()).doesNotContain(TestSecrets.CANARIES);
    }

    @Test
    void theShutdownRowIsWrittenAsTheContextCloses(CapturedOutput output) {
        RestartHarness.Boot boot = RestartHarness.boot(builder -> {
        });

        assertThat(boot.failure()).isNull();
        assertThat(onlyRow(output, "application-shutdown")).containsEntry("event.type", List.of("end"))
                .containsEntry("log.logger", "audit");
        assertThat(onlyRow(output, "application-startup")).containsEntry("labels.active_profiles",
                List.of("default")).containsEntry("labels.ipv6_prefix_length", 64);
    }

    private static Map<String, Object> onlyRow(CapturedOutput output, String action) {
        List<Map<String, Object>> rows = EcsJson.rowsWithAction(output.getOut(), action);
        assertThat(rows).as("rows with event.action %s", action).hasSize(1);
        return rows.get(0);
    }
}
