package sg.securedhello.config;

import static org.assertj.core.api.Assertions.assertThat;

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

    @ParameterizedTest
    @FieldSource("sg.securedhello.config.RequiredPropertiesPostProcessor#REQUIRED")
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
    void outsideDevATestSpeedBcryptCostStopsStartupBeforeThePortOpens() {
        Boot boot = RestartHarness.boot(builder -> { }, "--app.security.password.bcrypt-strength=4");

        assertThat(boot.portOpened()).isFalse();
        assertThat(boot.failureMessages()).contains("app.security.password.bcrypt-strength", "below 12");
    }

    @Test
    void outsideDevAPublishedDemoPasswordStopsStartupBeforeThePortOpens() {
        Boot boot = RestartHarness.boot(builder -> { }, "--app.admin.password=lantern-orchard-copper-tide");

        assertThat(boot.portOpened()).isFalse();
        assertThat(boot.failureMessages()).contains(TestSecrets.ADMIN_PASSWORD_PROPERTY, "published demo value")
                .doesNotContain("lantern-orchard-copper-tide");
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
}
