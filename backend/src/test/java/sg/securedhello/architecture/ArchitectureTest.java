package sg.securedhello.architecture;

import static sg.securedhello.architecture.ArchitectureRules.NO_AMBIENT_TIME;
import static sg.securedhello.architecture.ArchitectureRules.NO_RAW_CLIENT_ADDRESS;
import static sg.securedhello.architecture.ArchitectureRules.NO_REMEMBER_ME;
import static sg.securedhello.architecture.ArchitectureRules.NO_SLEEP;
import static sg.securedhello.architecture.ArchitectureRules.NO_SLICE_ON_PROVING_TESTS;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;

import org.junit.jupiter.api.Test;

import sg.securedhello.testsupport.Proves;

/** Applies {@link ArchitectureRules} to main and test code (level A). */
class ArchitectureTest {

    private static final String BASE_PACKAGE = "sg.securedhello";

    /** Deliberately violating classes that {@link ArchitectureRulesSelfTest} feeds to the rules. */
    private static final ImportOption EXCLUDE_FIXTURES =
            (Location location) -> !location.contains("/sg/securedhello/architecture/fixtures/");

    static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE_PACKAGE);

    static final JavaClasses TESTS = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
            .withImportOption(EXCLUDE_FIXTURES)
            .importPackages(BASE_PACKAGE);

    @Test
    @Proves("T-ARCH-001")
    void mainCodeReadsNoAmbientTime() {
        NO_AMBIENT_TIME.check(MAIN);
    }

    @Test
    @Proves("T-ARCH-001")
    void testsNeverSleep() {
        NO_SLEEP.check(TESTS);
    }

    @Test
    @Proves("T-ARCH-006")
    void provingTestsAreNeverBootTestSlices() {
        NO_SLICE_ON_PROVING_TESTS.check(TESTS);
    }

    @Test
    @Proves("T-RL-028")
    void onlyTheSourceKeyResolverReadsTheRawClientAddress() {
        NO_RAW_CLIENT_ADDRESS.check(MAIN);
    }

    /** REJ-008 names no test-plan row for remember-me; T-CFG-032 covers the cookie Max-Age half. */
    @Test
    void mainCodeNeverEnablesRememberMe() {
        NO_REMEMBER_ME.check(MAIN);
    }
}
