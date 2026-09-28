package sg.securedhello.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.TestSecrets;

/** Secret binding, key shape, key distinctness and fingerprints, on the {@link SecretsConfig} factory alone. */
@ExtendWith(OutputCaptureExtension.class)
class SecretsConfigTest {

    private static final Pattern FINGERPRINT_LINE =
            Pattern.compile("Key loaded: property=(\\S+) version=\\S+ fingerprint=([0-9a-f]+)\\b");

    private static final String[] KEYS = {SecretsConfig.TOTP_KEY, SecretsConfig.TOMBSTONE_KEY, SecretsConfig.LOG_KEY};

    static Stream<Arguments> malformedKeys() {
        Map<String, String> malformed = Map.of(
                "not Base64", "not base64 at all!",
                "unpadded Base64", Base64.getEncoder().withoutPadding().encodeToString(randomLooking(32)),
                "an unresolved placeholder", "${APP_KEY}",
                "16 bytes", Base64.getEncoder().encodeToString(randomLooking(16)),
                "33 bytes", Base64.getEncoder().encodeToString(randomLooking(33)),
                "printable ASCII", Base64.getEncoder().encodeToString("abcdefghijklmnopqrstuvwxyz012345".getBytes()),
                "identical bytes", Base64.getEncoder().encodeToString(new byte[32]));
        return Stream.of(KEYS).flatMap(property -> malformed.entrySet().stream()
                .map(bad -> Arguments.of(property, bad.getKey(), bad.getValue())));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("malformedKeys")
    @Proves("T-CFG-022")
    void aMalformedKeyRefusesStartupNamingThePropertyButNotTheValue(String property, String shape, String value,
            CapturedOutput output) {
        runner(Map.of(property, value)).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause()
                    .isInstanceOf(InvalidKeyMaterialException.class)
                    .hasMessageContaining(property)
                    .hasMessageNotContaining(value);
        });
        assertThat(output.getAll()).doesNotContain(value);
    }

    @Test
    @Proves("T-CFG-022")
    void aValidConfigurationLogsOneFingerprintOfEightHexCharactersPerKey(CapturedOutput output) {
        runner(Map.of()).run(context -> assertThat(context).hasNotFailed().hasSingleBean(ApplicationKeys.class));

        Matcher lines = FINGERPRINT_LINE.matcher(output.getOut());
        Stream.Builder<String> properties = Stream.builder();
        while (lines.find()) {
            properties.add(lines.group(1));
            assertThat(lines.group(2)).hasSize(8);
        }
        assertThat(properties.build()).containsExactly(KEYS);
        assertThat(output.getAll()).doesNotContain(TestSecrets.CANARIES);
    }

    @Test
    void theFingerprintIsADomainSeparatedDigestOfTheDecodedBytes() {
        KeyMaterial key = KeyMaterial.decode("k", 1, TestSecrets.TOTP_KEY);
        KeyMaterial same = KeyMaterial.decode("other", 2, TestSecrets.TOTP_KEY);
        KeyMaterial different = KeyMaterial.decode("k", 1, TestSecrets.LOG_KEY);

        assertThat(key.fingerprint()).matches("[0-9a-f]{8}").isEqualTo(same.fingerprint())
                .isNotEqualTo(different.fingerprint());
        assertThat(key.toString()).doesNotContain(TestSecrets.TOTP_KEY).contains(key.fingerprint());
        assertThat(key.bytes()).hasSize(KeyMaterial.LENGTH);
    }

    static Stream<Arguments> keyPairs() {
        return Stream.of(Arguments.of(SecretsConfig.TOTP_KEY, SecretsConfig.TOMBSTONE_KEY),
                Arguments.of(SecretsConfig.TOTP_KEY, SecretsConfig.LOG_KEY),
                Arguments.of(SecretsConfig.TOMBSTONE_KEY, SecretsConfig.LOG_KEY));
    }

    @ParameterizedTest(name = "{0} = {1}")
    @MethodSource("keyPairs")
    void reusingOneKeyForTwoPurposesRefusesStartup(String first, String second) {
        String shared = Base64.getEncoder().encodeToString(randomLooking(32));
        runner(Map.of(first, shared, second, shared)).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause()
                    .hasMessageContaining(first).hasMessageContaining(second).hasMessageNotContaining(shared);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {TestSecrets.TOTP_KEY_PROPERTY, TestSecrets.TOMBSTONE_KEY_PROPERTY,
            TestSecrets.LOG_KEY_PROPERTY, TestSecrets.SPA_ORIGIN_PROPERTY, TestSecrets.API_ORIGIN_PROPERTY,
            TestSecrets.TOTP_KEY_VERSION_PROPERTY, TestSecrets.TOMBSTONE_VERSION_PROPERTY,
            TestSecrets.ADMIN_USERNAME_PROPERTY, TestSecrets.ADMIN_PASSWORD_PROPERTY})
    @Proves("T-CFG-023")
    void anAbsentRequiredPropertyRefusesStartupNamingIt(String property) {
        Map<String, Object> properties = TestSecrets.properties();
        properties.remove(property);
        new ApplicationContextRunner()
                .withUserConfiguration(SecretsConfig.class)
                .withPropertyValues(pairs(properties))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(causeMessages(context.getStartupFailure())).contains(parentOrSelf(property));
                });
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 256})
    void aTotpKeyVersionOutsideOneByteRefusesStartup(int version) {
        runner(Map.of(TestSecrets.TOTP_KEY_VERSION_PROPERTY, String.valueOf(version)))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void propertyObjectsNeverPrintTheirSecrets() {
        assertThat(new TotpEncryptionProperties(TestSecrets.TOTP_KEY, 1).toString())
                .doesNotContain(TestSecrets.TOTP_KEY);
        assertThat(new HmacProperties(new HmacProperties.Tombstone(TestSecrets.TOMBSTONE_KEY, 1),
                new HmacProperties.Log(TestSecrets.LOG_KEY)).toString())
                .doesNotContain(TestSecrets.TOMBSTONE_KEY, TestSecrets.LOG_KEY);
        assertThat(new AdminSeedProperties("a", TestSecrets.ADMIN_PASSWORD).toString())
                .doesNotContain(TestSecrets.ADMIN_PASSWORD);
    }

    /** The test secrets with {@code overrides} applied. */
    private static ApplicationContextRunner runner(Map<String, String> overrides) {
        Map<String, Object> properties = TestSecrets.properties();
        properties.putAll(overrides);
        return new ApplicationContextRunner()
                .withUserConfiguration(SecretsConfig.class)
                .withPropertyValues(pairs(properties));
    }

    private static String[] pairs(Map<String, Object> properties) {
        return properties.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toArray(String[]::new);
    }

    /**
     * The name a binding failure reports: the field's path in camel case, or, for a nested object bound from a single
     * missing property, the object's path.
     */
    private static String parentOrSelf(String property) {
        String reported = property.equals(TestSecrets.LOG_KEY_PROPERTY) ? "app.security.hmac.log" : property;
        return Pattern.compile("-([a-z])").matcher(reported).replaceAll(m -> m.group(1).toUpperCase());
    }

    private static String causeMessages(Throwable failure) {
        StringBuilder messages = new StringBuilder();
        for (Throwable t = failure; t != null; t = t.getCause()) {
            messages.append(t.getMessage()).append('\n');
        }
        return messages.toString();
    }

    /** Deterministic, non-printable, non-uniform bytes. */
    private static byte[] randomLooking(int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) (i * 37 + 128);
        }
        return bytes;
    }
}
