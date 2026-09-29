package sg.securedhello.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationListener;
import org.springframework.util.PlaceholderResolutionException;

import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.RestartHarness;
import sg.securedhello.testsupport.RestartHarness.Boot;
import sg.securedhello.testsupport.TestSecrets;

/** Whole-application boots ({@code restart}) proving that bad configuration stops startup before the port opens. */
@ExtendWith(OutputCaptureExtension.class)
class StartupRefusalTest {

    private static final String UNRESOLVED = "${T_CFG_038_UNRESOLVED}";

    /**
     * The T-CFG-038 row's list, written out here rather than read from the implementation, so an omission from
     * {@code RequiredPropertiesPostProcessor.REQUIRED} fails. The row's OTLP URL under the export profile has no
     * profile to test yet (metrics export is off, ADR-061).
     */
    static final List<String> T_CFG_038_PROPERTIES = List.of("app.origins.spa", "app.origins.api",
            "app.mfa.totp.encryption.key-version", "app.security.hmac.tombstone.version", "app.mfa.totp.issuer");

    @Test
    void theTestSecretsBootTheApplication() {
        Boot boot = RestartHarness.boot(builder -> builder.profiles("dev"));

        assertThat(boot.failure()).isNull();
        assertThat(boot.portOpened()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {TestSecrets.TOTP_KEY_PROPERTY, TestSecrets.TOTP_KEY_VERSION_PROPERTY,
            TestSecrets.TOMBSTONE_KEY_PROPERTY, TestSecrets.TOMBSTONE_VERSION_PROPERTY, TestSecrets.LOG_KEY_PROPERTY,
            TestSecrets.ADMIN_USERNAME_PROPERTY, TestSecrets.ADMIN_PASSWORD_PROPERTY,
            TestSecrets.SPA_ORIGIN_PROPERTY, TestSecrets.API_ORIGIN_PROPERTY})
    @Proves("T-CFG-023")
    void aMissingSecretOrOriginStopsStartupBeforeThePortOpens(String property) {
        Boot boot = RestartHarness.boot(builder -> builder.profiles("dev")
                .initializers(RestartHarness.withoutTestSecret(property)));

        assertThat(boot.failure()).isNotNull();
        assertThat(boot.portOpened()).isFalse();
        // Naming the property is what tells this refusal from any other startup failure; no secret value is echoed.
        assertNamesMissingProperty(boot.failureMessages(), property);
        assertThat(boot.failureMessages()).doesNotContain(TestSecrets.CANARIES.toArray(String[]::new));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    @Proves("T-CFG-023")
    void aBlankTotpIssuerStopsStartupBeforeThePortOpens(String blank) {
        Boot boot = RestartHarness.boot(builder -> builder.profiles("dev"), "--app.mfa.totp.issuer=" + blank);

        assertThat(boot.portOpened()).isFalse();
        assertThat(boot.failureMessages()).contains("app.mfa.totp.issuer");
    }

    @ParameterizedTest
    @ValueSource(strings = {TestSecrets.TOTP_KEY_PROPERTY, TestSecrets.TOMBSTONE_KEY_PROPERTY,
            TestSecrets.LOG_KEY_PROPERTY})
    @Proves("T-CFG-022")
    void aMalformedKeyStopsStartupBeforeThePortOpensNamingThePropertyButNotTheValue(String property,
            CapturedOutput output) {
        String malformed = "TEST-ONLY-CANARY-malformed-key";
        Boot boot = RestartHarness.boot(builder -> builder.profiles("dev"), "--" + property + "=" + malformed);

        assertThat(boot.portOpened()).isFalse();
        assertThat(boot.failureMessages()).contains(property).doesNotContain(malformed);
        assertThat(output.getAll()).doesNotContain(malformed);
    }

    @Test
    void aReusedKeyStopsStartupBeforeThePortOpens() {
        Boot boot = RestartHarness.boot(builder -> builder.profiles("dev"),
                "--" + TestSecrets.LOG_KEY_PROPERTY + "=" + TestSecrets.TOMBSTONE_KEY);

        assertThat(boot.portOpened()).isFalse();
        assertThat(boot.failureMessages()).contains(TestSecrets.LOG_KEY_PROPERTY, TestSecrets.TOMBSTONE_KEY_PROPERTY);
    }

    @Test
    void theRequiredListIsTheTestPlanRow() {
        assertThat(RequiredPropertiesPostProcessor.REQUIRED)
                .as("a property added to or dropped from REQUIRED must be added to T-CFG-038 and to this test's list")
                .containsExactlyInAnyOrderElementsOf(T_CFG_038_PROPERTIES);
    }

    @ParameterizedTest
    @FieldSource("T_CFG_038_PROPERTIES")
    @Proves("T-CFG-038")
    void anUnresolvedPlaceholderInARequiredPropertyFailsRefresh(String property) {
        Boot boot = RestartHarness.boot(builder -> builder.profiles("dev"), "--" + property + "=" + UNRESOLVED);

        assertThat(boot.portOpened()).isFalse();
        assertThat(boot.failure()).isInstanceOf(PlaceholderResolutionException.class);
        assertThat(boot.failureMessages()).contains("T_CFG_038_UNRESOLVED");
    }

    @Test
    void aProhibitedSettingStopsStartupBeforeThePortOpens() {
        Boot boot = RestartHarness.boot(builder -> builder.profiles("dev"), "--spring.mvc.servlet.path=/api");

        assertThat(boot.portOpened()).isFalse();
        assertThat(boot.failureMessages()).contains("prohibited configuration", "spring.mvc.servlet.path");
    }

    @Test
    @Proves("T-CFG-010")
    void theResetLinkLoggerEnabledThroughSpringApplicationJsonOutsideDevRefusesToRun() {
        String json = "{\"logging\":{\"level\":{\"" + ResetLinkLoggerGuard.LOGGER_NAME + "\":\"DEBUG\"}}}";
        AtomicReference<LogLevel> arrived = new AtomicReference<>();
        ApplicationListener<ApplicationPreparedEvent> recordArrival = event -> arrived.set(LoggingSystem
                .get(getClass().getClassLoader())
                .getLoggerConfiguration(ResetLinkLoggerGuard.LOGGER_NAME).getEffectiveLevel());
        try {
            Boot boot = RestartHarness.boot(builder -> builder.listeners(recordArrival),
                    "--SPRING_APPLICATION_JSON=" + json);

            assertThat(arrived.get()).as("the override reached the logger").isEqualTo(LogLevel.DEBUG);
            assertThat(boot.portOpened()).as("refresh passed; the ready-time check refused").isTrue();
            assertThat(boot.failureMessages()).contains(ResetLinkLoggerGuard.LOGGER_NAME, "would log reset links");
        } finally {
            LoggingSystem.get(getClass().getClassLoader()).setLogLevel(ResetLinkLoggerGuard.LOGGER_NAME, null);
        }
    }

    @Test
    void theResetLinkLoggerAtItsDefaultLevelOutsideDevRuns() {
        Boot boot = RestartHarness.boot(builder -> { });

        assertThat(boot.failure()).isNull();
        assertThat(boot.portOpened()).isTrue();
    }

    /**
     * How the failure names a missing property: by itself, except where the binder reports the deepest path it could not
     * bind, a group whose only member is the missing key.
     */
    private static final Map<String, String> NAMED_IN_FAILURE_AS = Map.of(TestSecrets.LOG_KEY_PROPERTY,
            "Field error in object 'app.security.hmac' on field 'log': rejected value [null]");

    private static void assertNamesMissingProperty(String messages, String property) {
        assertThat(messages).as("the failure names %s", property)
                .contains(NAMED_IN_FAILURE_AS.getOrDefault(property, property));
    }
}
