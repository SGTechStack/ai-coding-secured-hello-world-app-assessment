package sg.securedhello.config;

import java.util.List;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Registers every presence-only required property with {@code setRequiredProperties}, so that an unresolved
 * {@code ${...}} placeholder, which the configuration-properties binder would accept as a literal, fails context
 * refresh in {@code prepareRefresh()} (ADR-062; R-CFG-019). Key material needs no entry: a placeholder fails its
 * strict Base64 decoding. Add each new presence-only required property here.
 */
public class RequiredPropertiesPostProcessor implements EnvironmentPostProcessor {

    /** The presence-only required properties (T-CFG-038). */
    static final List<String> REQUIRED = List.of(
            "app.origins.spa",
            "app.origins.api",
            "app.mfa.totp.encryption.key-version",
            "app.security.hmac.tombstone.version");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        environment.setRequiredProperties(REQUIRED.toArray(String[]::new));
    }
}
