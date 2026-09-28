package sg.securedhello.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Component;

/**
 * The refresh-phase prohibited-configuration validator (T-CFG-033). It runs as a bean factory post-processor, before
 * any application bean is created and so before the web server opens its port, and refuses startup on:
 * <ul>
 *   <li>{@code spring.mvc.servlet.path}, under which security matchers ignored the servlet path (REJ-024);</li>
 *   <li>{@code server.forward-headers-strategy=framework}, whose filter trusts any forwarded header (REJ-015);</li>
 *   <li>a session cookie {@code max-age}, which makes the cookie persistent (REJ-008);</li>
 *   <li>the dev-only reset-link logger's level set in a configuration file outside {@code dev} (ADR-057, control
 *       1; {@link ResetLinkLoggerGuard} is control 2);</li>
 *   <li>an unset or in-memory datasource URL, and under {@code dev} any URL that is not an H2 file (ADR-051).</li>
 * </ul>
 * Every refusal is collected and reported together. Messages name properties, never their values.
 */
@Component
public class ProhibitedConfigurationValidator implements BeanFactoryPostProcessor, EnvironmentAware {

    static final String DEV = "dev";

    private ConfigurableEnvironment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = (ConfigurableEnvironment) environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        List<String> violations = violations(environment);
        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                    "Startup refused: prohibited configuration:\n - " + String.join("\n - ", violations));
        }
    }

    /** Every prohibited setting present in {@code environment}; empty when startup may continue. */
    static List<String> violations(ConfigurableEnvironment environment) {
        List<String> violations = new ArrayList<>();
        boolean dev = environment.matchesProfiles(DEV);
        if (environment.containsProperty("spring.mvc.servlet.path")) {
            violations.add("spring.mvc.servlet.path is set; map controllers under /api instead (REJ-024)");
        }
        if ("framework".equalsIgnoreCase(trimmed(environment.getProperty("server.forward-headers-strategy")))) {
            violations.add("server.forward-headers-strategy=framework trusts any forwarded header; name trusted "
                    + "proxies instead (REJ-015)");
        }
        if (environment.containsProperty("server.servlet.session.cookie.max-age")) {
            violations.add("server.servlet.session.cookie.max-age is set; the session cookie must not persist "
                    + "(REJ-008)");
        }
        if (!dev && configuredInFile(environment, ResetLinkLoggerGuard.LEVEL_PROPERTY)) {
            violations.add(ResetLinkLoggerGuard.LEVEL_PROPERTY + " is set outside the dev profile (ADR-057)");
        }
        datasourceViolation(trimmed(environment.getProperty("spring.datasource.url")), dev)
                .ifPresent(violations::add);
        return violations;
    }

    private static Optional<String> datasourceViolation(String url, boolean dev) {
        if (url == null || url.isEmpty()) {
            return Optional.of("spring.datasource.url is unset, so an in-memory database would be used "
                    + "(ADR-051)");
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("jdbc:h2:") && lower.contains("mem:")) {
            return Optional.of("spring.datasource.url names an in-memory H2 database (ADR-051)");
        }
        if (dev && !lower.startsWith("jdbc:h2:file:")) {
            return Optional.of("spring.datasource.url is not an H2 file database under dev (ADR-051)");
        }
        return Optional.empty();
    }

    /** Whether a configuration file (config data, not the environment or command line) sets {@code property}. */
    private static boolean configuredInFile(ConfigurableEnvironment environment, String property) {
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (source instanceof OriginTrackedMapPropertySource && source.containsProperty(property)) {
                return true;
            }
        }
        return false;
    }

    private static String trimmed(String value) {
        return value == null ? null : value.trim();
    }
}
