package sg.securedhello.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.LazyInitializationBeanFactoryPostProcessor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import sg.securedhello.testsupport.TestSecrets;

/**
 * The demo keys and admin password published in {@code backend/README.md} are public, so outside {@code dev} startup
 * refuses them. The denylist holds fingerprints and digests only; this test reads the raw values from the README
 * itself, so a changed demo value that is not added to the denylist fails here.
 */
class PublishedDemoValuesTest {

    private static final Path README = Path.of("README.md");

    /** {@code APP_…_KEY = "…"} and {@code APP_ADMIN_PASSWORD = "…"} lines, in both the PowerShell and bash blocks. */
    private static final Pattern DEMO_LINE = Pattern.compile(
            "(APP_MFA_TOTP_ENCRYPTION_KEY|APP_SECURITY_HMAC_TOMBSTONE_KEY|APP_SECURITY_HMAC_LOG_KEY|APP_ADMIN_PASSWORD)"
                    + "\\s*=\\s*\"([^\"]+)\"");

    private static final Map<String, String> ENVIRONMENT_TO_PROPERTY = Map.of(
            "APP_MFA_TOTP_ENCRYPTION_KEY", TestSecrets.TOTP_KEY_PROPERTY,
            "APP_SECURITY_HMAC_TOMBSTONE_KEY", TestSecrets.TOMBSTONE_KEY_PROPERTY,
            "APP_SECURITY_HMAC_LOG_KEY", TestSecrets.LOG_KEY_PROPERTY,
            "APP_ADMIN_PASSWORD", TestSecrets.ADMIN_PASSWORD_PROPERTY);

    @Test
    void everyDemoValueInTheReadmeIsOnTheDenylist() throws IOException {
        Map<String, String> demo = readmeDemoValues();
        assertThat(demo).as("the README's demo block").containsOnlyKeys(ENVIRONMENT_TO_PROPERTY.keySet());

        for (String key : Set.of("APP_MFA_TOTP_ENCRYPTION_KEY", "APP_SECURITY_HMAC_TOMBSTONE_KEY",
                "APP_SECURITY_HMAC_LOG_KEY")) {
            String fingerprint = KeyMaterial.decode(key, null, demo.get(key)).fingerprint();
            assertThat(PublishedDemoValues.KEY_FINGERPRINTS).as(key).contains(fingerprint);
        }
        assertThat(PublishedDemoValues.isPublishedPassword(demo.get("APP_ADMIN_PASSWORD"))).isTrue();
    }

    @Test
    void theDenylistHoldsNoRawValue() throws IOException {
        String source = Files.readString(Path.of("src/main/java/sg/securedhello/config/PublishedDemoValues.java"));
        readmeDemoValues().values().forEach(value -> assertThat(source).doesNotContain(value));
    }

    @Test
    void theTestSecretsAreNotPublishedValues() {
        assertThat(PublishedDemoValues.isPublishedPassword(TestSecrets.ADMIN_PASSWORD)).isFalse();
        runner(Map.of()).run(context -> assertThat(context).hasNotFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"APP_MFA_TOTP_ENCRYPTION_KEY", "APP_SECURITY_HMAC_TOMBSTONE_KEY",
            "APP_SECURITY_HMAC_LOG_KEY", "APP_ADMIN_PASSWORD"})
    void outsideDevAPublishedDemoValueRefusesStartupNamingThePropertyButNotTheValue(String variable)
            throws IOException {
        String property = ENVIRONMENT_TO_PROPERTY.get(variable);
        String value = readmeDemoValues().get(variable);

        runner(Map.of(property, value)).run(context -> {
            assertThat(context).hasFailed();
            assertThat(causeMessages(context.getStartupFailure()))
                    .contains(property, "published demo value")
                    .doesNotContain(value);
        });
    }

    @Test
    void theRefusalStillRunsUnderLazyInitialization() throws IOException {
        String demoLogKey = readmeDemoValues().get("APP_SECURITY_HMAC_LOG_KEY");

        runner(Map.of(TestSecrets.LOG_KEY_PROPERTY, demoLogKey))
                .withInitializer(context -> context.addBeanFactoryPostProcessor(
                        new LazyInitializationBeanFactoryPostProcessor()))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void aDemoKeyInAnotherKeysSlotIsStillRefused() throws IOException {
        String demoLogKey = readmeDemoValues().get("APP_SECURITY_HMAC_LOG_KEY");

        runner(Map.of(TestSecrets.TOMBSTONE_KEY_PROPERTY, demoLogKey)).run(context -> {
            assertThat(context).hasFailed();
            assertThat(causeMessages(context.getStartupFailure())).contains(TestSecrets.TOMBSTONE_KEY_PROPERTY);
        });
    }

    @Test
    void everyPublishedValueIsReportedTogether() throws IOException {
        Map<String, String> demo = readmeDemoValues();
        Map<String, String> overrides = new HashMap<>();
        demo.forEach((variable, value) -> overrides.put(ENVIRONMENT_TO_PROPERTY.get(variable), value));

        runner(overrides).run(context -> assertThat(causeMessages(context.getStartupFailure()))
                .contains(ENVIRONMENT_TO_PROPERTY.values()));
    }

    @Test
    void underDevEveryPublishedDemoValueStarts() throws IOException {
        Map<String, String> overrides = new HashMap<>();
        readmeDemoValues().forEach((variable, value) -> overrides.put(ENVIRONMENT_TO_PROPERTY.get(variable), value));
        overrides.put("spring.profiles.active", "dev");

        runner(overrides).run(context -> assertThat(context).hasNotFailed()
                .hasSingleBean(PublishedDemoValues.class));
    }

    /** The demo values as the README publishes them; the PowerShell and bash blocks must agree. */
    private static Map<String, String> readmeDemoValues() throws IOException {
        Map<String, Set<String>> seen = new HashMap<>();
        Matcher matcher = DEMO_LINE.matcher(Files.readString(README));
        while (matcher.find()) {
            seen.computeIfAbsent(matcher.group(1), variable -> new TreeSet<>()).add(matcher.group(2));
        }
        Map<String, String> values = new HashMap<>();
        seen.forEach((variable, distinct) -> {
            assertThat(distinct).as(variable + " in both README blocks").hasSize(1);
            values.put(variable, distinct.iterator().next());
        });
        return values;
    }

    private static ApplicationContextRunner runner(Map<String, String> overrides) {
        Map<String, Object> properties = TestSecrets.properties();
        properties.putAll(overrides);
        return new ApplicationContextRunner()
                .withUserConfiguration(SecretsConfig.class, PublishedDemoValues.class)
                .withPropertyValues(properties.entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue()).toArray(String[]::new));
    }

    private static String causeMessages(Throwable failure) {
        StringBuilder messages = new StringBuilder();
        for (Throwable t = failure; t != null; t = t.getCause()) {
            messages.append(t.getMessage()).append('\n');
        }
        return messages.toString();
    }
}
