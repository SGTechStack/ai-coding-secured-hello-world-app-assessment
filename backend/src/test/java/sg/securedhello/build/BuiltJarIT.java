package sg.securedhello.build;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.junit.jupiter.api.Test;

import sg.securedhello.testsupport.Proves;

/**
 * The built jar carries no source-control metadata (ASVS 13.4.1 (L1); ASVS 13.4.6 (L3); ASVS 13.4.5 (L2)): no
 * {@code .git} or {@code .svn} entry, and no {@code git.properties} or {@code build-info.properties}, which would
 * publish the commit and build details. Runs under Failsafe, after {@code package} has written the jar; its path comes
 * from the Maven property {@code build.jar.path}.
 */
class BuiltJarIT {

    @Test
    @Proves("T-BLD-004")
    void theJarHoldsNoSourceControlOrBuildMetadata() throws IOException {
        Path jar = Path.of(System.getProperty("build.jar.path", "target/secured-hello-world-0.0.1-SNAPSHOT.jar"));
        assertThat(jar).isRegularFile();

        List<String> entries;
        try (JarFile file = new JarFile(jar.toFile())) {
            entries = Collections.list(file.entries()).stream().map(JarEntry::getName).toList();
        }

        assertThat(entries).as("entries of %s", jar).hasSizeGreaterThan(100)
                .anyMatch(name -> name.startsWith("BOOT-INF/classes/sg/securedhello/"));
        assertThat(entries).noneMatch(name -> List.of(name.split("/")).stream()
                .anyMatch(part -> part.equals(".git") || part.equals(".svn")));
        assertThat(entries).noneMatch(name -> name.endsWith("git.properties")
                || name.endsWith("build-info.properties"));
        assertThat(Files.size(jar)).isPositive();
    }
}
