package sg.securedhello.testsupport;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.IterableConfigurationPropertySource;
import org.springframework.core.env.ConfigurableEnvironment;

/** Property names as an environment sets them, for tests that assert a setting is absent from every source. */
public final class PropertyNames {

    /**
     * {@code management.endpoint.health.group.<name>.additional-path} in Boot's dashed form, the setting behind
     * CVE-2026-22731 (T-CFG-015).
     */
    public static final Pattern HEALTH_GROUP_ADDITIONAL_PATH =
            Pattern.compile("management\\.endpoint\\.health\\.group\\.[^.]+\\.additional-path");

    private PropertyNames() {
    }

    /** Every property name {@code environment} sets, across all of its sources, in Boot's dashed form. */
    public static List<String> of(ConfigurableEnvironment environment) {
        return StreamSupport.stream(ConfigurationPropertySources.get(environment).spliterator(), false)
                .filter(IterableConfigurationPropertySource.class::isInstance)
                .flatMap(source -> ((IterableConfigurationPropertySource) source).stream())
                .map(Object::toString)
                .toList();
    }

    /** Whether {@code name} is a health group's additional path. */
    public static boolean isHealthGroupAdditionalPath(String name) {
        return HEALTH_GROUP_ADDITIONAL_PATH.matcher(name).matches();
    }
}
