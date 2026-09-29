package sg.securedhello.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.registry.otlp.OtlpConfig;
import io.micrometer.registry.otlp.OtlpMeterRegistry;

import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.otlp.OtlpMetricsConnectionDetails;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.PlaceholderResolutionException;

import sg.securedhello.testsupport.CtxNondevTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.RestartHarness;
import sg.securedhello.testsupport.RestartHarness.Boot;

/**
 * ADR-061: metrics leave only by OTLP push, which ships switched off; the {@code otlp} profile switches it on and
 * makes the collector URL a required property, so a missing or unresolved URL stops startup (ADR-062).
 *
 * <p>These boots run the real configuration without the shared harness, whose own export switch (REJ-068) would
 * hide what the shipped configuration does.
 */
class OtlpExportProfileTest extends CtxNondevTest {

    private static final String URL_PROPERTY = RequiredPropertiesPostProcessor.OTLP_METRICS_URL;

    /** Nothing listens on the discard port, so a publish at shutdown fails fast and harmlessly. */
    private static final String UNUSED_COLLECTOR = "http://127.0.0.1:9/v1/metrics";

    @Test
    @Proves("T-OBS-012")
    void exportIsDormantInTheShippedConfiguration() {
        assertThat(productionProperty("management.otlp.metrics.export.enabled", Boolean.class)).isFalse();
        AtomicReference<String[]> present = new AtomicReference<>();

        Boot boot = RestartHarness.run(builder -> { }, context -> present.set(otlpBeans(context)));

        assertThat(boot.failure()).isNull();
        assertThat(present.get()).isEmpty();
    }

    @Test
    @Proves("T-CFG-038")
    void enablingExportWithoutAUrlStopsStartupBeforeThePortOpens() {
        Boot boot = RestartHarness.boot(builder -> builder.profiles("dev", "otlp"));

        assertThat(boot.portOpened()).isFalse();
        assertThat(boot.failure()).isInstanceOf(PlaceholderResolutionException.class);
        assertThat(boot.failureMessages()).contains("OTLP_METRICS_URL");
    }

    @Test
    @Proves("T-CFG-038")
    void anUnresolvedPlaceholderInTheUrlUnderTheExportProfileFailsRefresh() {
        Boot boot = RestartHarness.boot(builder -> builder.profiles("dev", "otlp"),
                "--" + URL_PROPERTY + "=${T_CFG_038_UNRESOLVED}");

        assertThat(boot.portOpened()).isFalse();
        assertThat(boot.failure()).isInstanceOf(PlaceholderResolutionException.class);
        assertThat(boot.failureMessages()).contains("T_CFG_038_UNRESOLVED");
    }

    @Test
    void anyConfigurationThatEnablesExportRequiresTheUrl() {
        Boot boot = RestartHarness.boot(builder -> builder.profiles("dev"),
                "--management.otlp.metrics.export.enabled=true");

        assertThat(boot.portOpened()).isFalse();
        assertThat(boot.failureMessages()).contains(URL_PROPERTY);
    }

    @Test
    void theProfileWithAUrlStartsAndExports() {
        AtomicReference<String[]> present = new AtomicReference<>();

        Boot boot = RestartHarness.run(builder -> builder.profiles("dev", "otlp"),
                context -> present.set(otlpBeans(context)), "--OTLP_METRICS_URL=" + UNUSED_COLLECTOR);

        assertThat(boot.failure()).isNull();
        assertThat(present.get()).hasSize(3);
    }

    private static String[] otlpBeans(ConfigurableApplicationContext context) {
        return Stream.of(OtlpMeterRegistry.class, OtlpConfig.class,
                        OtlpMetricsConnectionDetails.class)
                .flatMap(type -> Stream.of(context.getBeanNamesForType(type)))
                .toArray(String[]::new);
    }
}
