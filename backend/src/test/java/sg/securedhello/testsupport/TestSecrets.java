package sg.securedhello.testsupport;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Test-only values for every required secret and origin, which have no default in any profile (ADR-062). Every
 * context the harness builds gets them through {@link TemporaryH2FileInitializer}, at the lowest precedence, so a
 * test can still override or remove one.
 *
 * <p>The values are canaries: each key decodes to {@code TEST-ONLY-CANARY-...} ASCII followed by non-printable
 * padding, so it is valid key material yet obvious in any output. {@link #CANARIES} lists every secret value, encoded
 * and decoded, for a scan that asserts none of them ever reaches a log, audit row or response.
 */
public final class TestSecrets {

    public static final String TOTP_KEY_PROPERTY = "app.mfa.totp.encryption.key";
    public static final String TOTP_KEY_VERSION_PROPERTY = "app.mfa.totp.encryption.key-version";
    public static final String TOMBSTONE_KEY_PROPERTY = "app.security.hmac.tombstone.key";
    public static final String TOMBSTONE_VERSION_PROPERTY = "app.security.hmac.tombstone.version";
    public static final String LOG_KEY_PROPERTY = "app.security.hmac.log.key";
    public static final String ADMIN_USERNAME_PROPERTY = "app.admin.username";
    public static final String ADMIN_PASSWORD_PROPERTY = "app.admin.password";
    public static final String SPA_ORIGIN_PROPERTY = "app.origins.spa";
    public static final String API_ORIGIN_PROPERTY = "app.origins.api";

    public static final String TOTP_KEY_TEXT = "TEST-ONLY-CANARY-TOTP-KEY";
    public static final String TOMBSTONE_KEY_TEXT = "TEST-ONLY-CANARY-TOMBSTONE-KEY";
    public static final String LOG_KEY_TEXT = "TEST-ONLY-CANARY-LOG-KEY";

    public static final String TOTP_KEY = canaryKey(TOTP_KEY_TEXT);
    public static final String TOMBSTONE_KEY = canaryKey(TOMBSTONE_KEY_TEXT);
    public static final String LOG_KEY = canaryKey(LOG_KEY_TEXT);
    public static final String ADMIN_USERNAME = "canary-admin";
    /** Passes the password policy for {@link #ADMIN_USERNAME}, so every harness context seeds the bootstrap admin. */
    public static final String ADMIN_PASSWORD = "TEST-ONLY-CANARY-quartz-meadow-7f3a";

    /** Every secret value that must never appear in output: the encoded keys, their text and the admin password. */
    public static final List<String> CANARIES = List.of(TOTP_KEY, TOMBSTONE_KEY, LOG_KEY, TOTP_KEY_TEXT,
            TOMBSTONE_KEY_TEXT, LOG_KEY_TEXT, ADMIN_PASSWORD);

    /** The name of the property source {@link #addTo} adds. */
    public static final String SOURCE_NAME = "testSecrets";

    private TestSecrets() {
    }

    /** A fresh, mutable copy of every required secret and origin property. */
    public static Map<String, Object> properties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(TOTP_KEY_PROPERTY, TOTP_KEY);
        properties.put(TOTP_KEY_VERSION_PROPERTY, "1");
        properties.put(TOMBSTONE_KEY_PROPERTY, TOMBSTONE_KEY);
        properties.put(TOMBSTONE_VERSION_PROPERTY, "1");
        properties.put(LOG_KEY_PROPERTY, LOG_KEY);
        properties.put(ADMIN_USERNAME_PROPERTY, ADMIN_USERNAME);
        properties.put(ADMIN_PASSWORD_PROPERTY, ADMIN_PASSWORD);
        properties.put(SPA_ORIGIN_PROPERTY, "http://localhost:5173");
        properties.put(API_ORIGIN_PROPERTY, "http://localhost:8080");
        return properties;
    }

    /** Adds {@link #properties()} to {@code environment} at the lowest precedence. */
    public static void addTo(ConfigurableEnvironment environment) {
        environment.getPropertySources().addLast(new MapPropertySource(SOURCE_NAME, properties()));
    }

    /** 32 bytes: the ASCII {@code text}, then zero padding and a final 0xA5, so it is not all printable. */
    private static String canaryKey(String text) {
        byte[] key = new byte[32];
        byte[] ascii = text.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(ascii, 0, key, 0, ascii.length);
        key[31] = (byte) 0xA5;
        return Base64.getEncoder().encodeToString(key);
    }
}
