package sg.securedhello.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName.Form;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.IterableConfigurationPropertySource;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.boot.origin.Origin;
import org.springframework.boot.origin.TextResourceOrigin;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import sg.securedhello.persistence.DatabaseFile;

/**
 * The refresh-phase prohibited-configuration validator (T-CFG-033). It runs as a bean factory post-processor, before
 * any application bean is created and so before the web server opens its port, and refuses startup on:
 * <ul>
 *   <li>{@code spring.mvc.servlet.path}, under which security matchers ignored the servlet path (REJ-024);</li>
 *   <li>any {@code server.forward-headers-strategy} but {@code none}, unset included: {@code framework}'s filter trusts
 *       any forwarded header (REJ-015), and {@code native}, or cloud detection when unset, installs a valve that
 *       trusts Tomcat's RFC 1918 default; so does any {@code server.tomcat.remoteip.*} property. The only
 *       forwarded-header trust is the one derived from {@code app.security.client-ip} (R-RL-007; T-CFG-026);</li>
 *   <li>a session cookie {@code max-age}, which makes the cookie persistent (REJ-008);</li>
 *   <li>the dev-only reset-link logger's level set in a configuration file outside {@code dev} (ADR-057, control
 *       1; {@link ResetLinkLoggerGuard} is control 2);</li>
 *   <li>an unset or in-memory datasource URL, and under {@code dev} any URL that is not an H2 file (ADR-051);</li>
 *   <li>an H2 file datasource whose database file does not lie under {@code app.db.data-dir}, which the shed check,
 *       the {@code h2Data} indicator and the file-size gauge watch (R-OBS-019; T-OBS-016);</li>
 *   <li>the datasource URL rules in {@link DatasourceUrlRules}: {@code FILE_LOCK=NO}, {@code AUTO_SERVER} and, outside
 *       {@code dev}, an unpinned {@code LOCK_TIMEOUT}, {@code INIT} and any backslash in the settings (ADR-072;
 *       T-CFG-012; T-CFG-013). The settings in {@link #DATASOURCE_BYPASS_PREFIXES}, and every Hikari setting outside
 *       {@link #HIKARI_ALLOWED}, which would reach the database past those rules, are refused outright;</li>
 *   <li>any H2 server bean ({@value #H2_SERVER}), found by its declared type without creating it, which would let a
 *       second process into the database (ADR-072; R-CFG-021; T-CFG-014). A server started inside another bean, or
 *       declared as a supertype, is not visible here;</li>
 *   <li>with OTLP metrics export enabled, a collector URL that is not https (ADR-061; T-CFG-042). There is no
 *       {@code dev} exemption: export is opt-in through the {@code otlp} profile, and no precedent exempts dev;</li>
 *   <li>any clustering property: a shared session or limiter store, which the in-memory, single-instance rate
 *       limiter cannot follow (REJ-018; R-RL-006; T-CFG-029);</li>
 *   <li>Spring Session's schema initialisation set to anything but {@code never} (REJ-040; T-CFG-027);</li>
 *   <li>{@code show-values} on the env or configprops endpoint set to anything but {@code never} (R-OBS-011;
 *       T-CFG-028);</li>
 *   <li>a secret whose value comes from a configuration file on the classpath, that is, committed with the build
 *       (IM8 as-8; R-CFG-006; T-CFG-030).</li>
 * </ul>
 * Every refusal is collected and reported together. Messages name properties, never their values.
 */
@Component
public class ProhibitedConfigurationValidator implements BeanFactoryPostProcessor, EnvironmentAware {

    static final String DEV = "dev";

    /**
     * Property prefixes that configure state shared between instances. The limiters are in memory and the build
     * supports exactly one instance, so any of them would split every budget silently (REJ-018; R-RL-006).
     */
    static final List<String> CLUSTERING_PREFIXES = List.of("spring.data.redis", "spring.session.redis",
            "spring.session.hazelcast", "spring.session.mongodb", "spring.hazelcast", "bucket4j");

    static final String FORWARD_HEADERS_STRATEGY = "server.forward-headers-strategy";

    /** Tomcat's own forwarded-header settings, which Boot turns into a {@code RemoteIpValve} (R-RL-007). */
    static final String TOMCAT_REMOTE_IP = "server.tomcat.remoteip";

    /**
     * Settings that open their own connection or run inline SQL without passing through {@code spring.datasource.url},
     * so they would bypass {@link DatasourceUrlRules} (ADR-051; ADR-072). Settings that name script files to run
     * (Flyway locations and callbacks, JPA load scripts) are code shipped with the deployment, not configuration, and
     * are out of this validator's reach.
     */
    static final List<String> DATASOURCE_BYPASS_PREFIXES = List.of("spring.flyway.url", "spring.flyway.init-sqls",
            "spring.sql.init");

    /** The data directory, which must contain the database file (R-OBS-019). */
    static final String DATA_DIR = "app.db.data-dir";

    static final String HIKARI = "spring.datasource.hikari";

    /**
     * The only {@code spring.datasource.hikari.*} settings allowed: pool size, timeouts and naming. Every other Hikari
     * setting either replaces the URL, passes driver properties, or runs SQL on each connection, so it is refused
     * rather than listed one by one (ADR-072).
     */
    static final Set<String> HIKARI_ALLOWED = Set.of("maximum-pool-size", "minimum-idle", "connection-timeout",
            "idle-timeout", "max-lifetime", "keepalive-time", "validation-timeout", "leak-detection-threshold",
            "pool-name");

    /** H2 is a runtime dependency, so its server class is named, not linked. */
    static final String H2_SERVER = "org.h2.tools.Server";

    /** Spring Session's own schema initialisation, which must stay off: V7 owns the tables (REJ-040; ADR-030). */
    static final String SESSION_SCHEMA_INITIALIZATION = "spring.session.jdbc.initialize-schema";

    /** Actuator value masking, which may never be loosened above {@code never} (R-OBS-011). */
    static final List<String> SHOW_VALUES = List.of("management.endpoint.env.show-values",
            "management.endpoint.configprops.show-values");

    /**
     * Every secret, by property name or prefix (ADR-062): the three keys, the retired TOTP keys, the admin seed
     * credential and the OTLP export headers, which carry the collector's credential.
     */
    static final List<String> SECRET_PROPERTIES = List.of(SecretsConfig.TOTP_KEY, SecretsConfig.RETIRED_TOTP_KEYS,
            SecretsConfig.TOMBSTONE_KEY, SecretsConfig.LOG_KEY, "app.admin.username", "app.admin.password",
            "management.otlp.metrics.export.headers");

    private ConfigurableEnvironment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = (ConfigurableEnvironment) environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        List<String> violations = new ArrayList<>(violations(environment));
        h2ServerBeans(beanFactory).forEach(name -> violations.add("bean '" + name + "' is an H2 server ("
                + H2_SERVER + "), which would open the database to another process (ADR-072; R-CFG-021)"));
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
        if (!"none".equalsIgnoreCase(trimmed(environment.getProperty(FORWARD_HEADERS_STRATEGY)))) {
            violations.add(FORWARD_HEADERS_STRATEGY + " is not none; forwarded headers are trusted only from the "
                    + "proxies named in app.security.client-ip (REJ-015; R-RL-007)");
        }
        prefixesSet(environment, List.of(TOMCAT_REMOTE_IP)).forEach(prefix -> violations.add(prefix + ".* is set; "
                + "name trusted proxies in app.security.client-ip.trusted-proxies instead (R-RL-007)"));
        if (environment.containsProperty("server.servlet.session.cookie.max-age")) {
            violations.add("server.servlet.session.cookie.max-age is set; the session cookie must not persist "
                    + "(REJ-008)");
        }
        if (!dev && configuredInFile(environment, ResetLinkLoggerGuard.LEVEL_PROPERTY)) {
            violations.add(ResetLinkLoggerGuard.LEVEL_PROPERTY + " is set outside the dev profile (ADR-057)");
        }
        violations.addAll(DatasourceUrlRules.violations(environment.getProperty(DatasourceUrlRules.PROPERTY), dev));
        if (!dataDirectoryHoldsDatabase(environment)) {
            violations.add(DATA_DIR + " does not contain the database file " + DatasourceUrlRules.PROPERTY + " names; "
                    + "the storage checks would watch the wrong mount (R-OBS-019)");
        }
        hikariSettingsNotAllowed(environment).forEach(setting -> violations.add(HIKARI + "." + setting + " is set; "
                + "only pool sizing, timeouts and the pool name may be set on Hikari (ADR-072)"));
        prefixesSet(environment, DATASOURCE_BYPASS_PREFIXES).forEach(prefix -> violations.add(prefix + " is set; "
                + "configure the database through " + DatasourceUrlRules.PROPERTY + " only (ADR-072)"));
        prefixesSet(environment, CLUSTERING_PREFIXES).forEach(prefix -> violations.add(prefix + ".* is set; the rate "
                + "limiters are in memory and only one instance is supported (REJ-018; R-RL-006)"));
        String otlpUrl = environment.getProperty(RequiredPropertiesPostProcessor.OTLP_METRICS_URL);
        if (otlpUrl != null && RequiredPropertiesPostProcessor.otlpExportEnabled(environment) && !isHttps(otlpUrl)) {
            violations.add(RequiredPropertiesPostProcessor.OTLP_METRICS_URL + " is not an https URL; metrics must "
                    + "not leave in clear (ADR-061)");
        }
        String sessionSchema = trimmed(environment.getProperty(SESSION_SCHEMA_INITIALIZATION));
        if (sessionSchema != null && !"never".equalsIgnoreCase(sessionSchema)) {
            violations.add(SESSION_SCHEMA_INITIALIZATION + " is not never; V7 owns the session tables (REJ-040)");
        }
        SHOW_VALUES.stream().filter(property -> {
            String value = trimmed(environment.getProperty(property));
            return value != null && !"never".equalsIgnoreCase(value);
        }).forEach(property -> violations.add(property + " is not never; actuator values stay masked (R-OBS-011)"));
        secretsFromClasspathFiles(environment).forEach(secret -> violations.add(secret + " comes from a committed "
                + "configuration file; supply it from a mounted secret or the environment (IM8 as-8; R-CFG-006)"));
        return violations;
    }

    /**
     * Whether the H2 file the datasource URL names lies under {@code app.db.data-dir}. Nothing to check while either is
     * unset or the URL is not an H2 file; an unset data directory fails its own binding instead. A blank one names no
     * directory, so it holds nothing.
     */
    private static boolean dataDirectoryHoldsDatabase(ConfigurableEnvironment environment) {
        String dataDir = trimmed(environment.getProperty(DATA_DIR));
        String url = environment.getProperty(DatasourceUrlRules.PROPERTY);
        if (dataDir == null || url == null || DatabaseFile.databasePath(url).isEmpty()) {
            return true;
        }
        if (dataDir.isEmpty()) {
            return false;
        }
        try {
            return DatabaseFile.underDataDirectory(url, Path.of(dataDir));
        } catch (InvalidPathException unreadable) {
            return false;
        }
    }

    /**
     * Every secret set by a configuration file on the classpath, which is committed with the build. A mounted file or
     * the environment is where a secret belongs (ADR-062).
     */
    private static Set<String> secretsFromClasspathFiles(ConfigurableEnvironment environment) {
        Set<String> found = new TreeSet<>();
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (source instanceof OriginTrackedMapPropertySource file) {
                for (String name : file.getPropertyNames()) {
                    SECRET_PROPERTIES.stream()
                            .filter(secret -> isOrUnder(secret, name) && fromClasspath(file.getOrigin(name)))
                            .forEach(found::add);
                }
            }
        }
        return found;
    }

    private static boolean isOrUnder(String secret, String name) {
        ConfigurationPropertyName root = ConfigurationPropertyName.of(secret);
        ConfigurationPropertyName candidate = ConfigurationPropertyName.adapt(name, '.');
        return root.equals(candidate) || root.isAncestorOf(candidate);
    }

    private static boolean fromClasspath(Origin origin) {
        for (Origin at = origin; at != null; at = at.getParent()) {
            if (at instanceof TextResourceOrigin text && text.getResource() instanceof ClassPathResource) {
                return true;
            }
        }
        return false;
    }

    /** Whether the set {@code url} is a well-formed URL with an https scheme, in any letter case, and a host. */
    private static boolean isHttps(String url) {
        try {
            URI uri = new URI(url.trim());
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /** The {@code prefixes} under which any property is set, in any spelling Boot would bind. */
    private static Set<String> prefixesSet(ConfigurableEnvironment environment, List<String> prefixes) {
        Set<String> found = new TreeSet<>();
        for (ConfigurationPropertySource source : ConfigurationPropertySources.get(environment)) {
            if (source instanceof IterableConfigurationPropertySource names) {
                names.stream().forEach(name -> prefixes.stream()
                        .filter(prefix -> {
                            ConfigurationPropertyName root = ConfigurationPropertyName.of(prefix);
                            return root.equals(name) || root.isAncestorOf(name);
                        })
                        .forEach(found::add));
            }
        }
        return found;
    }

    /** The first element under {@code spring.datasource.hikari} of every set property not in the allowlist. */
    private static Set<String> hikariSettingsNotAllowed(ConfigurableEnvironment environment) {
        ConfigurationPropertyName hikari = ConfigurationPropertyName.of(HIKARI);
        int depth = hikari.getNumberOfElements();
        Set<String> allowed = new TreeSet<>();
        HIKARI_ALLOWED.forEach(setting -> allowed.add(
                ConfigurationPropertyName.of(HIKARI + "." + setting).getElement(depth, Form.UNIFORM)));
        Set<String> found = new TreeSet<>();
        for (ConfigurationPropertySource source : ConfigurationPropertySources.get(environment)) {
            if (source instanceof IterableConfigurationPropertySource names) {
                names.stream().filter(hikari::isAncestorOf)
                        .filter(name -> !allowed.contains(name.getElement(depth, Form.UNIFORM)))
                        .forEach(name -> found.add(name.getElement(depth, Form.DASHED)));
            }
        }
        return found;
    }

    /** Every bean whose type is H2's server, found from bean definitions without creating any bean. */
    private static List<String> h2ServerBeans(ConfigurableListableBeanFactory beanFactory) {
        ClassLoader classLoader = beanFactory.getBeanClassLoader();
        if (!ClassUtils.isPresent(H2_SERVER, classLoader)) {
            return List.of();
        }
        return List.of(beanFactory.getBeanNamesForType(ClassUtils.resolveClassName(H2_SERVER, classLoader), true,
                false));
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
