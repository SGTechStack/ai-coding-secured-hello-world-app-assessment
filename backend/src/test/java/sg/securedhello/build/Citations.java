package sg.securedhello.build;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import sg.securedhello.testsupport.Proves;

/**
 * Collects the T-IDs that tests cite, keyed by ID, with where each citation was found (ADR-068).
 *
 * <p>Java tests cite with {@link Proves}; Vitest and Playwright tests put the ID in the test name. The frontend is
 * read as source text, so the Maven build needs no Node runtime.
 */
final class Citations {

    /** A test or suite call and its literal name: {@code it('...'}, {@code test.skip("..."}, {@code describe(`...`}. */
    private static final Pattern TEST_NAME = Pattern.compile(
            "\\b(?:describe|it|test)(?:\\.\\w+)*\\(\\s*(['\"`])((?:\\\\.|(?!\\1).)*)\\1", Pattern.DOTALL);

    private static final Pattern CITED_ID = Pattern.compile("\\bT-[A-Z][A-Z0-9]*-\\d+\\b");

    private static final Set<String> SKIPPED_DIRS =
            Set.of("node_modules", "dist", "coverage", "test-results", "playwright-report", "blob-report");

    private final Map<String, Set<String>> byId = new TreeMap<>();

    Map<String, Set<String>> byId() {
        return byId;
    }

    void add(String id, String location) {
        byId.computeIfAbsent(id, key -> new TreeSet<>()).add(location);
    }

    /** Adds every {@code @Proves} value on the test classes under {@code basePackage}, at class or method level. */
    Citations addJavaTests(String basePackage) {
        Iterable<JavaClass> classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                .importPackages(basePackage);
        for (JavaClass javaClass : classes) {
            javaClass.tryGetAnnotationOfType(Proves.class)
                    .ifPresent(proves -> addAll(proves.value(), javaClass.getName()));
            for (JavaMethod method : javaClass.getMethods()) {
                method.tryGetAnnotationOfType(Proves.class)
                        .ifPresent(proves -> addAll(proves.value(), javaClass.getName() + "#" + method.getName()));
            }
        }
        return this;
    }

    /** Adds the T-IDs in the test names of {@code *.test.ts(x)} and {@code *.spec.ts(x)} files under {@code root}. */
    Citations addFrontendTests(Path root) {
        if (!Files.isDirectory(root)) {
            throw new GateFailure("The frontend directory " + root + " is missing. Check the Maven property "
                    + "traceability.frontend.dir.");
        }
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return SKIPPED_DIRS.contains(dir.getFileName().toString())
                            ? FileVisitResult.SKIP_SUBTREE
                            : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.getFileName().toString().matches(".+\\.(test|spec)\\.tsx?")) {
                        addFrontendSource(Files.readString(file),
                                root.relativize(file).toString().replace('\\', '/'));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    void addFrontendSource(String source, String location) {
        Matcher name = TEST_NAME.matcher(source);
        while (name.find()) {
            Matcher id = CITED_ID.matcher(name.group(2));
            while (id.find()) {
                add(id.group(), location);
            }
        }
    }

    private void addAll(String[] ids, String location) {
        for (String id : ids) {
            add(id, location);
        }
    }
}
