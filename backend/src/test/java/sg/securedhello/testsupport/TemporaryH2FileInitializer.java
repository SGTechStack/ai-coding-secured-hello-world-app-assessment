package sg.securedhello.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.beans.factory.support.DefaultSingletonBeanRegistry;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

/**
 * Points {@code spring.datasource.url} at a fresh H2 file in a new temporary directory, one per context (ADR-067).
 * In-memory H2 is never used. The directory is deleted when the context closes.
 *
 * <p>The lock timeout defaults to production's 1000 ms; a context overrides it with {@value #LOCK_TIMEOUT_PROPERTY}.
 */
public class TemporaryH2FileInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    /** Test-only property naming the H2 {@code LOCK_TIMEOUT} in milliseconds. */
    public static final String LOCK_TIMEOUT_PROPERTY = "test-harness.h2.lock-timeout-ms";

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        Path directory = createTemporaryDirectory();
        String database = directory.resolve("secured-hello").toString().replace('\\', '/');
        String url = "jdbc:h2:file:" + database + ";LOCK_TIMEOUT=${" + LOCK_TIMEOUT_PROPERTY + ":1000}";
        context.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("temporaryH2File", Map.of("spring.datasource.url", url)));
        // Registered before any other bean, so it is destroyed last: after the pool has closed the database.
        ((DefaultSingletonBeanRegistry) context.getBeanFactory())
                .registerDisposableBean("temporaryH2FileCleanup", () -> deleteQuietly(directory));
    }

    private static Path createTemporaryDirectory() {
        try {
            return Files.createTempDirectory("secured-hello-h2-").toRealPath();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void deleteQuietly(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException ignored) {
            // Best effort: a leftover temporary directory is harmless.
        }
    }
}
