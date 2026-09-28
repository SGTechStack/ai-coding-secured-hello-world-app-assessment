package sg.securedhello.testsupport;

import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;

/**
 * {@code ctx-nondev}: asserts production values without ever refreshing a production application context (ADR-067).
 *
 * <p>Two tools, both reading the real {@code application.yml} with no profile active:
 * <ul>
 *   <li>{@link #productionProperty} binds a single production value, e.g. the datasource URL;</li>
 *   <li>{@link #productionContextRunner()} refreshes only the configuration classes a test names, on a temporary H2
 *       file, e.g. to check the encoder's production cost.</li>
 * </ul>
 */
public abstract class CtxNondevTest {

    /** The production environment: {@code application.yml} (and system/env values), no profile. */
    protected static ConfigurableEnvironment productionEnvironment() {
        ConfigurableEnvironment environment = new StandardEnvironment();
        ConfigDataEnvironmentPostProcessor.applyTo(environment);
        return environment;
    }

    /** Binds {@code name} from the production environment; fails if it is absent. */
    protected static <T> T productionProperty(String name, Class<T> type) {
        return Binder.get(productionEnvironment()).bind(name, type)
                .orElseThrow(() -> new AssertionError("Production configuration has no " + name));
    }

    /** A runner with the production configuration and no profile, on a temporary H2 file. */
    protected static ApplicationContextRunner productionContextRunner() {
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withInitializer(new TemporaryH2FileInitializer());
    }
}
