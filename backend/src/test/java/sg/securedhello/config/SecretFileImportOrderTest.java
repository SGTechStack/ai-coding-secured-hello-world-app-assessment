package sg.securedhello.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import sg.securedhello.testsupport.Proves;

/**
 * A mounted secret file beats a local {@code .env} file for the same key (ASVS 13.3.1 (L2) compensating; R-CFG-013).
 * The two imports are siblings in one document of {@code application.yml}, where a later import wins, so their order
 * is the control: this reads that order from the committed file and replays it against two temporary directories.
 */
class SecretFileImportOrderTest {

    private static final String DOT_ENV = "optional:file:./.env[.properties]";
    private static final String CONFIG_TREE = "optional:configtree:/run/secrets/";
    private static final String SECRET = "app.admin.password";

    @TempDir
    Path workingDirectory;

    @TempDir
    Path secrets;

    /** {@code spring.config.import} as {@code application.yml} writes it, in order. */
    private static List<String> committedImports() throws IOException {
        List<PropertySource<?>> documents = new YamlPropertySourceLoader().load("application.yml",
                new ClassPathResource("application.yml"));
        Map<?, ?> first = (Map<?, ?>) documents.getFirst().getSource();
        return List.of(String.valueOf(first.get("spring.config.import[0]")),
                String.valueOf(first.get("spring.config.import[1]")));
    }

    @Test
    @Proves("T-CFG-021")
    void theMountedSecretFileWinsOverTheDotEnvImport() throws IOException {
        List<String> imports = committedImports();
        assertThat(imports).containsExactly(DOT_ENV, CONFIG_TREE);

        Path dotEnv = workingDirectory.resolve(".env");
        Files.writeString(dotEnv, SECRET + "=from-dot-env\n", StandardCharsets.UTF_8);
        Files.writeString(secrets.resolve(SECRET), "from-mounted-file", StandardCharsets.UTF_8);
        String replayed = String.join(",",
                imports.get(0).replace("./.env", dotEnv.toString().replace('\\', '/')),
                imports.get(1).replace("/run/secrets/", secrets.toString().replace('\\', '/') + "/"));

        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("imports", Map.of(
                "spring.config.import", replayed,
                "spring.config.location", "optional:classpath:/no-such-location/")));
        ConfigDataEnvironmentPostProcessor.applyTo(environment);

        assertThat(environment.getProperty(SECRET)).isEqualTo("from-mounted-file");
    }

    @Test
    @Proves("T-CFG-021")
    void theDotEnvImportAloneStillBinds() throws IOException {
        Path dotEnv = workingDirectory.resolve(".env");
        Files.writeString(dotEnv, SECRET + "=from-dot-env\n", StandardCharsets.UTF_8);

        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("imports", Map.of(
                "spring.config.import", DOT_ENV.replace("./.env", dotEnv.toString().replace('\\', '/')),
                "spring.config.location", "optional:classpath:/no-such-location/")));
        ConfigDataEnvironmentPostProcessor.applyTo(environment);

        assertThat(environment.getProperty(SECRET)).isEqualTo("from-dot-env");
    }
}
