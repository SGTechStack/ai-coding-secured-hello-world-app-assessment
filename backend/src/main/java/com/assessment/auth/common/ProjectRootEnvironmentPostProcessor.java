package com.assessment.auth.common;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Resolves {@code app.project-root} to an absolute path before any property placeholder is read.
 *
 * <p>Story 1.2 requires the dev H2 JDBC URL to be pinned absolute from the project root, "so that
 * running from {@code backend/} and from the repo root use the same database". A relative H2 URL
 * silently creates a <em>second</em> database file when the working directory changes, and the
 * first symptom is a bootstrap admin that appears to have vanished.
 *
 * <p>The root is found by walking up from the working directory looking for {@code backend/pom.xml}
 * — the one marker that is present in a source checkout and unambiguous. If no marker is found the
 * working directory is used, which is the correct fallback for a packaged jar.
 */
public class ProjectRootEnvironmentPostProcessor implements EnvironmentPostProcessor {

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "projectRoot", Map.of("app.project-root", resolveProjectRoot().toString())));
  }

  private Path resolveProjectRoot() {
    Path start = Paths.get("").toAbsolutePath().normalize();
    for (Path candidate = start; candidate != null; candidate = candidate.getParent()) {
      if (Files.isRegularFile(candidate.resolve("backend").resolve("pom.xml"))) {
        return candidate;
      }
    }
    return start;
  }
}
