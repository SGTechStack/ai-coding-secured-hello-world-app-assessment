package sg.securedhello.testsupport;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.CommandLinePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

import sg.securedhello.SecuredHelloApplication;
import sg.securedhello.config.ResetLinkLoggerGuard;

/**
 * {@code restart}: boots the whole application through {@code SpringApplication}, on its own temporary H2 file and a
 * random port, with the {@link TestSecrets} and, under {@code dev} only, BCrypt cost 4. Used where a test needs a real
 * startup, e.g. to prove that startup is refused before the port opens. The context is closed before {@link #boot}
 * returns.
 */
public final class RestartHarness {

    /** A random port, as a command-line argument so it outranks application.yml. */
    private static final String[] HARNESS_ARGUMENTS = {"--server.port=0"};

    private static final String BCRYPT_STRENGTH = "app.security.password.bcrypt-strength";

    /**
     * The test-speed BCrypt cost 4, under {@code dev} only: outside {@code dev} {@code PasswordEncoderConfig} refuses
     * a cost below 12 (ADR-001), so a non-dev boot hashes at the production cost. It sits just below the
     * command line, so a caller's {@code --app.security.password.bcrypt-strength} still wins.
     */
    private static final ApplicationContextInitializer<ConfigurableApplicationContext> TEST_SPEED_BCRYPT_UNDER_DEV =
            context -> {
                ConfigurableEnvironment environment = context.getEnvironment();
                if (!environment.matchesProfiles("dev")) {
                    return;
                }
                MutablePropertySources sources = environment.getPropertySources();
                MapPropertySource cost = new MapPropertySource("harnessBcryptCost", Map.of(BCRYPT_STRENGTH, "4"));
                if (sources.contains(CommandLinePropertySource.COMMAND_LINE_PROPERTY_SOURCE_NAME)) {
                    sources.addAfter(CommandLinePropertySource.COMMAND_LINE_PROPERTY_SOURCE_NAME, cost);
                } else {
                    sources.addFirst(cost);
                }
            };

    private RestartHarness() {
    }

    /** What one boot did. */
    public record Boot(boolean portOpened, Throwable failure) {

        /** Every message in the failure's cause chain, joined; empty if the boot succeeded. */
        public String failureMessages() {
            List<String> messages = new ArrayList<>();
            for (Throwable t = failure; t != null; t = t.getCause()) {
                messages.add(String.valueOf(t.getMessage()));
            }
            return String.join("\n", messages);
        }
    }

    /**
     * Boots and closes the application once. {@code customiser} adds profiles, initializers or listeners;
     * {@code args} are command-line arguments ({@code --name=value}), the way to override a property.
     */
    public static Boot boot(Consumer<SpringApplicationBuilder> customiser, String... args) {
        return run(customiser, context -> { }, args);
    }

    /**
     * As {@link #boot}, and runs {@code whileRunning} against the started application before closing it, for a test
     * that must act on a real startup. A failure in {@code whileRunning} is the boot's failure.
     */
    public static Boot run(Consumer<SpringApplicationBuilder> customiser,
            Consumer<ConfigurableApplicationContext> whileRunning, String... args) {
        AtomicBoolean portOpened = new AtomicBoolean();
        // Logback is one per JVM, and a dev context booted earlier in the suite enabled the dev-only link logger
        // (ADR-057). A restart is a fresh process, so it starts from the logger's default level, as production would.
        LoggingSystem.get(RestartHarness.class.getClassLoader()).setLogLevel(ResetLinkLoggerGuard.LOGGER_NAME, null);
        SpringApplicationBuilder builder = new SpringApplicationBuilder(SecuredHelloApplication.class)
                .initializers(new TemporaryH2FileInitializer(), TEST_SPEED_BCRYPT_UNDER_DEV)
                .listeners((ApplicationListener<WebServerInitializedEvent>) event -> portOpened.set(true));
        customiser.accept(builder);
        // A caller's argument replaces the harness's own for the same property: a repeated option would bind as a list.
        String[] arguments = Stream.concat(Stream.of(HARNESS_ARGUMENTS)
                        .filter(harness -> Stream.of(args).noneMatch(arg -> arg.startsWith(harness.split("=")[0] + "="))),
                Stream.of(args)).toArray(String[]::new);
        try (ConfigurableApplicationContext context = builder.run(arguments)) {
            whileRunning.accept(context);
            return new Boot(portOpened.get(), null);
        } catch (Throwable failure) {
            return new Boot(portOpened.get(), failure);
        }
    }

    /**
     * An initializer, to run after {@link TemporaryH2FileInitializer}, that points the datasource at an H2 file in
     * {@code directory} instead of a fresh one, so successive boots share a database (persistence across boots). The
     * caller owns the directory, typically a JUnit {@code @TempDir}.
     */
    public static ApplicationContextInitializer<ConfigurableApplicationContext> onDatabase(Path directory) {
        String database = directory.resolve("secured-hello").toString().replace('\\', '/');
        return context -> context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                "restartDatabase", Map.of("spring.datasource.url", "jdbc:h2:file:" + database + ";LOCK_TIMEOUT=1000")));
    }

    /** An initializer, to run after {@link TemporaryH2FileInitializer}, that removes one test secret. */
    public static ApplicationContextInitializer<ConfigurableApplicationContext> withoutTestSecret(String property) {
        return context -> {
            Map<String, Object> properties = TestSecrets.properties();
            properties.remove(property);
            context.getEnvironment().getPropertySources().replace(TestSecrets.SOURCE_NAME,
                    new MapPropertySource(TestSecrets.SOURCE_NAME, properties));
        };
    }
}
