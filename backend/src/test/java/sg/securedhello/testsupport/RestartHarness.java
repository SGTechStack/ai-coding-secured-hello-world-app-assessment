package sg.securedhello.testsupport;

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
import org.springframework.core.env.MapPropertySource;

import sg.securedhello.SecuredHelloApplication;
import sg.securedhello.config.ResetLinkLoggerGuard;

/**
 * {@code restart}: boots the whole application through {@code SpringApplication}, on its own temporary H2 file and a
 * random port, with the {@link TestSecrets}. Used where a test needs a real startup, e.g. to prove that startup is
 * refused before the port opens. The context is closed before {@link #boot} returns.
 */
public final class RestartHarness {

    /** A random port and the test-speed BCrypt cost, as command-line arguments so they outrank application.yml. */
    private static final String[] HARNESS_ARGUMENTS = {"--server.port=0", "--app.security.password.bcrypt-strength=4"};

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
        AtomicBoolean portOpened = new AtomicBoolean();
        // Logback is one per JVM, and a dev context booted earlier in the suite enabled the dev-only link logger
        // (ADR-057). A restart is a fresh process, so it starts from the logger's default level, as production would.
        LoggingSystem.get(RestartHarness.class.getClassLoader()).setLogLevel(ResetLinkLoggerGuard.LOGGER_NAME, null);
        SpringApplicationBuilder builder = new SpringApplicationBuilder(SecuredHelloApplication.class)
                .initializers(new TemporaryH2FileInitializer())
                .listeners((ApplicationListener<WebServerInitializedEvent>) event -> portOpened.set(true));
        customiser.accept(builder);
        String[] arguments = Stream.concat(Stream.of(HARNESS_ARGUMENTS), Stream.of(args)).toArray(String[]::new);
        try (ConfigurableApplicationContext ignored = builder.run(arguments)) {
            return new Boot(portOpened.get(), null);
        } catch (Throwable failure) {
            return new Boot(portOpened.get(), failure);
        }
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
