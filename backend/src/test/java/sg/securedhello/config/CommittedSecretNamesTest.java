package sg.securedhello.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.boot.env.PropertySourceLoader;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import sg.securedhello.testsupport.Proves;

/**
 * No secret's property name appears in any committed configuration file, so no profile can carry a default for it
 * (ADR-062). Each file is checked twice: as raw text, and as the flattened keys Boot loads from it, which catches a
 * name split across nested YAML levels.
 */
class CommittedSecretNamesTest {

    private static final List<Path> RESOURCE_ROOTS =
            List.of(Path.of("src/main/resources"), Path.of("src/test/resources"));

    /** {@code app.admin.password} must also be absent from the example environment file. */
    private static final Path ENV_EXAMPLE = Path.of("../.env.example");

    /** The device-cookie key (ADR-075), whose dev value alone is committed, in {@link #DEV_CONFIGURATION}. */
    private static final String DEVICE_SECRET = "app.security.lockout.device.secret";

    private static final Path DEV_CONFIGURATION = Path.of("src/main/resources/application-dev.yml");

    @ParameterizedTest
    @ValueSource(strings = {"app.mfa.totp.encryption.key", "app.security.hmac.tombstone.key",
            "app.security.hmac.log.key", "app.admin.password", "app.admin.username",
            "management.otlp.metrics.export.headers", "app.security.lockout.device.secret"})
    @Proves("T-CFG-020")
    void noCommittedConfigurationFileNamesTheSecret(String secret) throws IOException {
        Pattern name = namePattern(secret);
        List<Path> files = configurationFiles();
        if (secret.equals("app.admin.password") && Files.exists(ENV_EXAMPLE)) {
            files.add(ENV_EXAMPLE);
        }

        if (secret.equals(DEVICE_SECRET)) {
            // Its dev value is published in application-dev.yml, which only dev loads, and which startup refuses
            // outside dev by fingerprint (ADR-075; T-CFG-039). Every other file is scanned as for any secret.
            files.removeIf(file -> file.endsWith(DEV_CONFIGURATION));
        }
        assertThat(files).as("configuration files to scan").isNotEmpty();
        for (Path file : files) {
            assertThat(name.matcher(Files.readString(file)).find()).as("%s names %s", file, secret).isFalse();
            assertThat(flattenedKeys(file)).as("keys loaded from %s", file)
                    .noneMatch(key -> name.matcher(key).find());
        }
    }

    @Test
    void theNamePatternAcceptsEverySpellingOfASegmentJoin() {
        Pattern name = namePattern("app.admin.password");

        assertThat(Stream.of("app.admin.password: x", "APP_ADMIN_PASSWORD=x", "app-admin-password", "appAdminPassword",
                "admin:\n  password").filter(text -> name.matcher(text).find())).hasSize(4);
        assertThat(name.matcher("app.admin.password-policy").find()).isFalse();
        assertThat(namePattern("management.otlp.metrics.export.headers").matcher(
                "management.otlp.metrics.export.headers.Authorization").find()).isTrue();
    }

    /** Segments joined by dot, dash, underscore or nothing (a camel hump), case-insensitively. */
    private static Pattern namePattern(String property) {
        String joined = String.join("[._-]?", Stream.of(property.split("\\.")).map(Pattern::quote).toList());
        return Pattern.compile("(?<![a-z0-9])" + joined + "(?![a-z0-9-])", Pattern.CASE_INSENSITIVE);
    }

    private static List<Path> configurationFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        for (Path root : RESOURCE_ROOTS) {
            if (Files.isDirectory(root)) {
                try (Stream<Path> paths = Files.walk(root)) {
                    paths.filter(CommittedSecretNamesTest::isConfiguration).forEach(files::add);
                }
            }
        }
        return files;
    }

    private static boolean isConfiguration(Path path) {
        String file = path.getFileName().toString();
        return file.endsWith(".yml") || file.endsWith(".yaml") || file.endsWith(".properties");
    }

    private static List<String> flattenedKeys(Path file) throws IOException {
        String name = file.getFileName().toString();
        PropertySourceLoader loader = name.endsWith(".properties") ? new PropertiesPropertySourceLoader()
                : name.startsWith(".env") ? null : new YamlPropertySourceLoader();
        if (loader == null) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        for (PropertySource<?> source : loader.load(name, new FileSystemResource(file))) {
            keys.addAll(List.of(((EnumerablePropertySource<?>) source).getPropertyNames()));
        }
        return keys;
    }
}
