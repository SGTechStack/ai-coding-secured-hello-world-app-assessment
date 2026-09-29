package sg.securedhello.build;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.platform.commons.annotation.Testable;

import sg.securedhello.testsupport.Proves;

/**
 * Collects the T-IDs that tests cite, keyed by ID, with where each citation was found (ADR-068).
 *
 * <p>Java tests cite with {@link Proves}; Vitest and Playwright tests put the ID in the test name. The frontend is
 * read as source text by {@link FrontendTestNames}, so the Maven build needs no Node runtime. A class-level
 * {@code @Proves} is read on the concrete class only; {@code Proves} is not inherited.
 */
final class Citations {

    /**
     * Surefire's and Failsafe's default includes, by simple class name. {@code pom.xml} sets no includes; if it ever
     * does, change this pattern with it.
     */
    private static final Pattern RUNNER_CLASS_NAME = Pattern.compile("Test.*|.*Test|.*Tests|.*TestCase|IT.*|.*IT|.*ITCase");

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

    /**
     * Adds every {@code @Proves} value on the test classes under {@code basePackage} that a runner executes. A citation
     * counts only on a JUnit test method (anything meta-annotated with {@link Testable}), or on a class with one, of a
     * concrete class that Surefire or Failsafe picks up by name (or a {@link Nested} class inside one), and never under
     * {@link Disabled}. Test methods inherited from an abstract base are cited by each concrete subclass.
     */
    Citations addJavaTests(String basePackage) {
        Iterable<JavaClass> classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                .importPackages(basePackage);
        for (JavaClass javaClass : classes) {
            if (!isRun(javaClass)) {
                continue;
            }
            List<JavaMethod> tests = testMethods(javaClass);
            if (!tests.isEmpty()) {
                javaClass.tryGetAnnotationOfType(Proves.class)
                        .ifPresent(proves -> addAll(proves.value(), javaClass.getName()));
            }
            for (JavaMethod method : tests) {
                method.tryGetAnnotationOfType(Proves.class)
                        .ifPresent(proves -> addAll(proves.value(), javaClass.getName() + "#" + method.getName()));
            }
        }
        return this;
    }

    /** The class's JUnit test methods, inherited ones included, that are not {@link Disabled}. */
    static List<JavaMethod> testMethods(JavaClass javaClass) {
        return javaClass.getAllMethods().stream()
                .filter(method -> method.isMetaAnnotatedWith(Testable.class))
                .filter(method -> !method.isAnnotatedWith(Disabled.class))
                .toList();
    }

    /** Whether a runner executes the class: concrete, not disabled, and named for Surefire or Failsafe, or nested. */
    static boolean isRun(JavaClass javaClass) {
        if (javaClass.isInterface() || javaClass.getModifiers().contains(JavaModifier.ABSTRACT)
                || javaClass.isAnnotatedWith(Disabled.class)) {
            return false;
        }
        return javaClass.getEnclosingClass()
                .map(outer -> javaClass.isAnnotatedWith(Nested.class) && isRun(outer))
                .orElseGet(() -> RUNNER_CLASS_NAME.matcher(javaClass.getSimpleName()).matches());
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

    /** Adds the T-IDs in the names of the tests in {@code source} that run ({@link FrontendTestNames}). */
    void addFrontendSource(String source, String location) {
        for (String name : FrontendTestNames.runningTestNames(source)) {
            Matcher id = CITED_ID.matcher(name);
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
