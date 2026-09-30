package sg.securedhello.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Refuses startup outside {@code dev} when a key or the seed administrator's password is one of the shared demo values
 * published in {@code backend/README.md}, past or present. Those values are in git, so a deployment that pastes them
 * would run with a public TOTP key, public log and tombstone HMAC keys, and a public admin password.
 *
 * <p>Only fingerprints and digests are held here, never a value. Keys are compared by their logged
 * {@link KeyMaterial#fingerprint()}, in any key slot. The password is compared by its SHA-256 digest; the value is
 * public, so the digest protects nothing and only keeps the literal out of main code. {@code PublishedDemoValuesTest}
 * reads the README and fails if a published value is missing from these lists. Never lazy: nothing injects it, so
 * under {@code spring.main.lazy-initialization} it would otherwise never run.
 *
 * <p>The dev-only demo accounts ({@code sg.securedhello.demo}) publish three more values in {@code application-dev.yml}:
 * two passwords and a TOTP secret. Outside {@code dev} their passwords are refused as {@code app.admin.password} like
 * the others, any {@value #DEMO_ACCOUNTS} property is refused, and, once every singleton exists and the schema is
 * migrated but before the port opens, so is a database still holding an activated demo account, which a copied dev
 * database would carry with its public password and secret.
 */
@Component
@Lazy(false)
public class PublishedDemoValues implements SmartInitializingSingleton {

    /** The dev-only demo accounts' properties, in {@code application-dev.yml}. */
    public static final String DEMO_ACCOUNTS = "app.dev.demo-accounts";

    /** The demo user's username. */
    public static final String DEMO_USER = "demo-user";

    /** The demo administrator's username. */
    public static final String DEMO_ADMIN = "demo-admin";

    /** The reserved, never-deliverable domain (RFC 2606) of the demo accounts' addresses, which marks them as seeded. */
    public static final String DEMO_EMAIL_DOMAIN = "demo.invalid";

    /** Activated accounts that are a seeded demo account: the demo username with its seeded address. */
    private static final String DEMO_ACCOUNT_ROWS = """
            SELECT COUNT(*) FROM users WHERE activated_at IS NOT NULL
            AND ((username = ? AND email = ?) OR (username = ? AND email = ?))""";

    /** Fingerprints of the README's demo TOTP, tombstone HMAC and log HMAC keys. */
    static final Set<String> KEY_FINGERPRINTS = Set.of("4d7e3f33", "8ae7cde4", "72e91456");

    /** SHA-256 hex digests of every demo admin password the README has published. */
    private static final Set<String> PASSWORD_DIGESTS = Set.of(
            "58ac3b75a6d742823d5c873c8c32d3d913cd68577ffeb861c82d05cec0eeed26",
            "2ec7e88f970986651d851fbbb29b5a063ae0ac4c71bf10577d5deeafe58a6b79",
            // The dev-only demo user's and demo administrator's passwords (application-dev.yml).
            "0c97f014476ff5cd652d6a64c7cf3dd2ef76d396f1f35ee38a0066501b3e8883",
            "4d7f472f96a7ad77875c2a82f73b771f20d24001471a7f05cb3a789ad8fd66e6");

    private static final String ADMIN_PASSWORD = "app.admin.password";

    private final boolean dev;
    private final ObjectProvider<JdbcTemplate> jdbc;

    /**
     * @throws IllegalStateException outside {@code dev}, naming every property that holds a published demo value and
     *                               never the value
     */
    public PublishedDemoValues(Environment environment, ApplicationKeys keys, AdminSeedProperties admin,
            ObjectProvider<JdbcTemplate> jdbc) {
        this.dev = environment.matchesProfiles(ProhibitedConfigurationValidator.DEV);
        this.jdbc = jdbc;
        if (dev) {
            return;
        }
        List<String> published = new ArrayList<>();
        keys.all().stream().filter(key -> KEY_FINGERPRINTS.contains(key.fingerprint()))
                .forEach(key -> published.add(key.property()));
        if (isPublishedPassword(admin.password())) {
            published.add(ADMIN_PASSWORD);
        }
        if (Binder.get(environment).bind(DEMO_ACCOUNTS, Bindable.mapOf(String.class, Object.class)).isBound()) {
            published.add(DEMO_ACCOUNTS);
        }
        if (!published.isEmpty()) {
            throw new IllegalStateException("Startup refused: " + String.join(", ", published) + " holds a published "
                    + "demo value from backend/README.md or application-dev.yml, which is public; generate fresh "
                    + "values outside dev");
        }
    }

    /** The seeded address of the demo account {@code username}. */
    public static String demoEmail(String username) {
        return username + "@" + DEMO_EMAIL_DOMAIN;
    }

    /**
     * @throws IllegalStateException outside {@code dev}, when the database holds an activated demo account
     */
    @Override
    public void afterSingletonsInstantiated() {
        JdbcTemplate database = jdbc.getIfAvailable();
        if (dev || database == null) {
            return;
        }
        Integer demoAccounts = database.queryForObject(DEMO_ACCOUNT_ROWS, Integer.class, DEMO_USER,
                demoEmail(DEMO_USER), DEMO_ADMIN, demoEmail(DEMO_ADMIN));
        if (demoAccounts != null && demoAccounts > 0) {
            throw new IllegalStateException("Startup refused: the database holds a dev-only demo account ("
                    + DEMO_USER + " or " + DEMO_ADMIN + "), whose credentials are public; delete it under dev, or"
                    + " start outside dev on a database that was never run under dev");
        }
    }

    static boolean isPublishedPassword(String password) {
        return PASSWORD_DIGESTS.contains(HexFormat.of().formatHex(sha256(password.getBytes(StandardCharsets.UTF_8))));
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is mandatory on every Java platform", e);
        }
    }
}
