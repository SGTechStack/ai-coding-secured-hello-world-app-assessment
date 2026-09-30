package sg.securedhello.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.boot.origin.OriginTrackedValue;
import org.springframework.boot.origin.TextResourceOrigin;
import org.springframework.core.io.FileSystemResource;

import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.TestSecrets;

/**
 * A rejected configuration value, key material above all, never reaches any log line at any level, stdout, stderr or
 * any throwable, and neither does where it came from (LOG §3.3:225; LOG §3.3:223; LOG §3.2:199). The refusal carries
 * the property's name and, for a key, only the observed length.
 */
@ExtendWith(OutputCaptureExtension.class)
class RejectedValueNeverLoggedTest {

    /**
     * Where the values come from: a mounted secret file whose path must not surface either. The framework's own TRACE
     * lines name the property source being searched, so the source's name is neutral and the origin is the file.
     */
    private static final String ORIGIN = "origin-canary-6b0d2f";

    private static final FileSystemResource MOUNT = new FileSystemResource("/run/secrets/" + ORIGIN);

    private final LoggerContext logging = (LoggerContext) LoggerFactory.getILoggerFactory();
    private Level rootLevel;

    @BeforeEach
    void everyLevel() {
        Logger root = logging.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        rootLevel = root.getLevel();
        root.setLevel(Level.TRACE);
    }

    @AfterEach
    void restoreLevel() {
        logging.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).setLevel(rootLevel);
    }

    private static ApplicationContextRunner runner(Class<?> configuration, Map<String, Object> overrides) {
        Map<String, Object> properties = new HashMap<>(TestSecrets.properties());
        properties.putAll(overrides);
        Map<String, Object> tracked = new HashMap<>();
        properties.forEach((name, value) -> tracked.put(name, OriginTrackedValue.of(value,
                new TextResourceOrigin(MOUNT, new TextResourceOrigin.Location(2, 3)))));
        return new ApplicationContextRunner().withUserConfiguration(configuration).withInitializer(context -> context
                .getEnvironment().getPropertySources().addFirst(new OriginTrackedMapPropertySource("mounted",
                        tracked)));
    }

    private static String everythingThrown(Throwable failure) {
        StringWriter trace = new StringWriter();
        failure.printStackTrace(new PrintWriter(trace));
        return trace.toString();
    }

    @Test
    @Proves("T-AUD-022")
    void aWrongLengthKeyRefusesStartupWithItsLengthOnly(CapturedOutput output) {
        byte[] sixteen = new byte[16];
        for (int i = 0; i < sixteen.length; i++) {
            sixteen[i] = (byte) (0x80 + i * 7);
        }
        String value = Base64.getEncoder().encodeToString(sixteen);

        runner(SecretsConfig.class, Map.of(SecretsConfig.LOG_KEY, value)).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().isInstanceOf(InvalidKeyMaterialException.class)
                    .hasMessage("Startup refused: " + SecretsConfig.LOG_KEY
                            + " decodes to 16 bytes; exactly 32 are required");
            assertThat(everythingThrown(context.getStartupFailure())).doesNotContain(value, ORIGIN);
        });
        assertThat(output.getAll()).doesNotContain(value, ORIGIN);
    }

    @Test
    @Proves("T-AUD-022")
    void aMalformedKeyAndAProhibitedSettingAreRefusedWithoutEchoingEither(CapturedOutput output) {
        String key = "not-base64-key-canary-4e91!";
        String url = "jdbc:h2:mem:url-canary-4e91";

        runner(SecretsConfig.class, Map.of(SecretsConfig.TOMBSTONE_KEY, key)).run(context -> {
            assertThat(context).hasFailed();
            assertThat(everythingThrown(context.getStartupFailure())).doesNotContain(key, ORIGIN);
        });
        runner(ProhibitedConfigurationValidator.class, Map.of("spring.datasource.url", url)).run(context -> {
            assertThat(context).hasFailed();
            assertThat(everythingThrown(context.getStartupFailure())).contains("spring.datasource.url")
                    .doesNotContain(url, "url-canary-4e91", ORIGIN);
        });
        assertThat(output.getAll()).doesNotContain(key, url, "url-canary-4e91", ORIGIN);
    }
}
