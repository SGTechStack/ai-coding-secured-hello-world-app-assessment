package sg.securedhello.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
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
 */
@Component
@Lazy(false)
public class PublishedDemoValues {

    /** Fingerprints of the README's demo TOTP, tombstone HMAC and log HMAC keys. */
    static final Set<String> KEY_FINGERPRINTS = Set.of("4d7e3f33", "8ae7cde4", "72e91456");

    /** SHA-256 hex digests of every demo admin password the README has published. */
    private static final Set<String> PASSWORD_DIGESTS = Set.of(
            "58ac3b75a6d742823d5c873c8c32d3d913cd68577ffeb861c82d05cec0eeed26",
            "2ec7e88f970986651d851fbbb29b5a063ae0ac4c71bf10577d5deeafe58a6b79");

    private static final String ADMIN_PASSWORD = "app.admin.password";

    /**
     * @throws IllegalStateException outside {@code dev}, naming every property that holds a published demo value and
     *                               never the value
     */
    public PublishedDemoValues(Environment environment, ApplicationKeys keys, AdminSeedProperties admin) {
        if (environment.matchesProfiles(ProhibitedConfigurationValidator.DEV)) {
            return;
        }
        List<String> published = new ArrayList<>();
        keys.all().stream().filter(key -> KEY_FINGERPRINTS.contains(key.fingerprint()))
                .forEach(key -> published.add(key.property()));
        if (isPublishedPassword(admin.password())) {
            published.add(ADMIN_PASSWORD);
        }
        if (!published.isEmpty()) {
            throw new IllegalStateException("Startup refused: " + String.join(", ", published) + " holds a published "
                    + "demo value from backend/README.md, which is public; generate fresh values outside dev");
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
