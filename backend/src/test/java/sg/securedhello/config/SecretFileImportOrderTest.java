package sg.securedhello.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import sg.securedhello.testsupport.Proves;

/**
 * Where a secret may come from besides the environment (ADR-062; R-CFG-013): files mounted under {@code /run/secrets}
 * in every profile, and a local {@code .env} file in {@code dev} only. Which import is active, and which wins, is
 * decided by the documents of {@code application.yml} and their order, so this loads the committed file itself, with
 * the two locations pointed at temporary directories, under each profile.
 */
class SecretFileImportOrderTest {

    private static final String DOT_ENV = "./.env";
    private static final String CONFIG_TREE = "/run/secrets/";
    private static final String SECRET = "app.admin.password";

    @TempDir
    Path workingDirectory;

    @TempDir
    Path secrets;

    @TempDir
    Path configuration;

    private void dotEnv() throws IOException {
        Files.writeString(workingDirectory.resolve(".env"), SECRET + "=from-dot-env\n", StandardCharsets.UTF_8);
    }

    private void mountedSecret() throws IOException {
        Files.writeString(secrets.resolve(SECRET), "from-mounted-file", StandardCharsets.UTF_8);
    }

    /** {@link #SECRET} as the committed {@code application.yml} binds it under {@code profiles}. */
    private String bound(String... profiles) throws IOException {
        String committed = new ClassPathResource("application.yml").getContentAsString(StandardCharsets.UTF_8);
        assertThat(committed).contains(DOT_ENV, CONFIG_TREE);
        Files.writeString(configuration.resolve("application.yml"), committed
                .replace(DOT_ENV, slashes(workingDirectory.resolve(".env")))
                .replace(CONFIG_TREE, slashes(secrets) + "/"), StandardCharsets.UTF_8);

        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles(profiles);
        environment.getPropertySources().addFirst(new MapPropertySource("location",
                Map.of("spring.config.location", slashes(configuration) + "/")));
        ConfigDataEnvironmentPostProcessor.applyTo(environment);
        return environment.getProperty(SECRET);
    }

    private static String slashes(Path path) {
        return path.toString().replace('\\', '/');
    }

    @Test
    @Proves("T-CFG-021")
    void inDevTheMountedSecretFileWinsOverTheDotEnvFile() throws IOException {
        dotEnv();
        mountedSecret();

        assertThat(bound("dev")).isEqualTo("from-mounted-file");
    }

    @Test
    @Proves("T-CFG-043")
    void inDevTheDotEnvFileIsRead() throws IOException {
        dotEnv();

        assertThat(bound("dev")).isEqualTo("from-dot-env");
    }

    @Test
    @Proves("T-CFG-043")
    void outsideDevTheDotEnvFileIsIgnoredAndTheMountedSecretFileIsRead() throws IOException {
        dotEnv();

        assertThat(bound()).as("no profile").isNull();
        assertThat(bound("otlp")).as("another profile").isNull();

        mountedSecret();
        assertThat(bound()).isEqualTo("from-mounted-file");
    }
}
