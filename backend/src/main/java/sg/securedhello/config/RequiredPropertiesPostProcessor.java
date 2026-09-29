package sg.securedhello.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Registers every presence-only required property with {@code setRequiredProperties}, so that an unresolved
 * {@code ${...}} placeholder, which the configuration-properties binder would accept as a literal, fails context
 * refresh in {@code prepareRefresh()} (ADR-062; R-CFG-019). Key material needs no entry: a placeholder fails its
 * strict Base64 decoding. Add each new presence-only required property here.
 *
 * <p>The OTLP metrics URL is required only when metrics export is enabled, which the base configuration never is and
 * the {@code otlp} profile is (ADR-061). A plain {@code ${OTLP_METRICS_URL}} placeholder would not be enough: the
 * binder keeps an unresolved one as a literal URL (ADR-062).
 */
public class RequiredPropertiesPostProcessor implements EnvironmentPostProcessor {

    /** The presence-only required properties (T-CFG-038). */
    static final List<String> REQUIRED = List.of(
            "app.origins.spa",
            "app.origins.api",
            "app.mfa.totp.encryption.key-version",
            "app.security.hmac.tombstone.version",
            "app.mfa.totp.issuer");

    /** Required whenever OTLP metrics export is enabled (T-CFG-038). */
    static final String OTLP_METRICS_URL = "management.otlp.metrics.export.url";

    private static final String OTLP_EXPORT_ENABLED = "management.otlp.metrics.export.enabled";

    /** Boot's fallback when a registry's own export switch is unset ({@code @ConditionalOnEnabledMetricsExport}). */
    private static final String DEFAULT_EXPORT_ENABLED = "management.defaults.metrics.export.enabled";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        List<String> required = new ArrayList<>(REQUIRED);
        if (otlpExportEnabled(environment)) {
            required.add(OTLP_METRICS_URL);
        }
        environment.setRequiredProperties(required.toArray(String[]::new));
    }

    /** Resolved the way Boot's OTLP metrics export auto-configuration decides it. */
    static boolean otlpExportEnabled(ConfigurableEnvironment environment) {
        Boolean enabled = environment.getProperty(OTLP_EXPORT_ENABLED, Boolean.class);
        return enabled != null ? enabled : environment.getProperty(DEFAULT_EXPORT_ENABLED, Boolean.class, true);
    }
}
