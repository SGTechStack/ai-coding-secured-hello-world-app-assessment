package local.builderday;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PackagedApplicationIT {
  private static final Pattern GENERATED_ASSET = Pattern.compile("(?:src|href)=\"(/assets/[^\"]+)\"");

  @Test
  void should_packageFrontendDistributionInOneExecutableJar_whenMavenBuildsApplication() throws Exception {
    Path applicationJar = Path.of("target", "builderday-0.0.1-SNAPSHOT.jar");
    Path distribution = Path.of("..", "frontend", "dist");

    try (var distributionFiles = Files.walk(distribution);
        var jar = new JarFile(applicationJar.toFile())) {
      var distributionPaths = distributionFiles.filter(Files::isRegularFile)
          .map(distribution::relativize).sorted().toList();

      assertThat(distributionPaths).isNotEmpty();
      assertThat(jar.getManifest().getMainAttributes().getValue("Start-Class"))
          .isEqualTo(BuilderdayApplication.class.getName());
      assertThat(jar.getJarEntry("BOOT-INF/classes/db/changelog/db.changelog-master.yaml")).isNotNull();
      assertThat(jar.stream().map(entry -> entry.getName()))
          .noneMatch(name -> name.contains("db.changelog-local")
              || name.endsWith("002-insert-illustrative-user.yaml"));
      // Cloud environments run PostgreSQL; the in-memory H2 is for local runs and tests only.
      assertThat(jar.stream().map(entry -> entry.getName()))
          .anyMatch(name -> name.matches("BOOT-INF/lib/postgresql-.*\\.jar"))
          .noneMatch(name -> name.matches("BOOT-INF/lib/h2-.*\\.jar"));

      for (Path relativePath : distributionPaths) {
        assertThat(jar.getJarEntry("BOOT-INF/classes/static/" + relativePath.toString().replace('\\', '/')))
            .as("packaged static resource %s", relativePath)
            .isNotNull();
      }

      var indexEntry = jar.getJarEntry("BOOT-INF/classes/static/index.html");
      assertThat(indexEntry).isNotNull();
      String document = new String(jar.getInputStream(indexEntry).readAllBytes(), StandardCharsets.UTF_8);
      assertThat(document).contains("__CSP_NONCE__");
      var assets = GENERATED_ASSET.matcher(document).results().map(result -> result.group(1)).toList();
      assertThat(assets).isNotEmpty();
      assertThat(assets)
          .allSatisfy(asset -> assertThat(jar.getJarEntry("BOOT-INF/classes/static" + asset)).isNotNull());
    }
  }
}
