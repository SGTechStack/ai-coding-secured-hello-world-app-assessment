package sg.securedhello.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static sg.securedhello.architecture.ArchitectureRules.ADMIN_CONTROLLERS_REACH_NO_PERSISTENCE;
import static sg.securedhello.architecture.ArchitectureRules.AUDIT_EMIT_TAKES_NO_THROWABLE;
import static sg.securedhello.architecture.ArchitectureRules.ONLY_THE_GUARDED_SERVICE_ENABLES_OR_DISABLES;
import static sg.securedhello.architecture.ArchitectureRules.ONLY_THE_GUARDED_SERVICE_CHANGES_ROLES_OR_DELETES;
import static sg.securedhello.architecture.ArchitectureRules.ONLY_THE_GUARDED_SERVICE_RESETS_FACTORS;
import static sg.securedhello.architecture.ArchitectureRules.NO_ACCOUNT_LOOKUP_BEFORE_AUTHENTICATION;
import static sg.securedhello.architecture.ArchitectureRules.NO_AMBIENT_TIME;
import static sg.securedhello.architecture.ArchitectureRules.NO_CSRF_COOKIE;
import static sg.securedhello.architecture.ArchitectureRules.NO_CSRF_TEST_POST_PROCESSOR;
import static sg.securedhello.architecture.ArchitectureRules.NO_SEND_ERROR;
import static sg.securedhello.architecture.ArchitectureRules.NO_RAW_CLIENT_ADDRESS;
import static sg.securedhello.architecture.ArchitectureRules.NO_REMEMBER_ME;
import static sg.securedhello.architecture.ArchitectureRules.NO_SLEEP;
import static sg.securedhello.architecture.ArchitectureRules.NO_SLICE_ON_PROVING_TESTS;
import static sg.securedhello.architecture.ArchitectureRules.NO_THROWABLE_ON_AUDIT_ROWS;
import static sg.securedhello.architecture.ArchitectureRules.ONLY_PASSWORD_SERVICE_ENCODES;
import static sg.securedhello.architecture.ArchitectureRules.ONLY_PASSWORD_SERVICE_WRITES_THE_CREDENTIAL;
import static sg.securedhello.architecture.ArchitectureRules.RESET_PATHS_AVOID_THE_AUTHENTICATION_MANAGER;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

import sg.securedhello.admin.AdminActions;
import sg.securedhello.admin.AdminUserController;
import sg.securedhello.mfa.TotpFactorRemoval;
import sg.securedhello.password.PasswordService;
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
    @Proves("T-AUTH-011")
    void mainCodeNeverCallsSendError() {
        NO_SEND_ERROR.check(MAIN);
    }

    @Test
    @Proves("T-RL-028")
    void onlyTheSourceKeyResolverReadsTheRawClientAddress() {
        NO_RAW_CLIENT_ADDRESS.check(MAIN);
    }

    /** ADR-001's "no lockout branch before authentication"; its behavioural rows are T-RL-017 and T-AUTH-003. */
    @Test
    void nothingAheadOfTheProviderLooksAnAccountUp() {
        NO_ACCOUNT_LOOKUP_BEFORE_AUTHENTICATION.check(MAIN);
    }

    /** REJ-008 names no test-plan row for remember-me; T-CFG-032 covers the cookie Max-Age half. */
    @Test
    void mainCodeNeverEnablesRememberMe() {
        NO_REMEMBER_ME.check(MAIN);
    }

    @Test
    @Proves("T-AUD-016")
    void theAuditEmitterTakesNoThrowable() {
        AUDIT_EMIT_TAKES_NO_THROWABLE.check(MAIN);
    }

    @Test
    @Proves("T-AUD-016")
    void noAuditCodeAttachesAThrowableToALogEvent() {
        NO_THROWABLE_ON_AUDIT_ROWS.check(MAIN);
    }

    @Test
    @Proves("T-CRED-005")
    void passwordServiceIsTheSoleCallerOfEncode() {
        ONLY_PASSWORD_SERVICE_ENCODES.check(MAIN);
        // Exactly one caller, not none: the rule alone would pass vacuously if the call left main code.
        assertThat(MAIN.get(PasswordService.class).getMethodCallsFromSelf())
                .anyMatch(call -> call.getName().equals("encode"));
    }

    @Test
    @Proves("T-ADM-014")
    void adminControllersReachPersistenceOnlyThroughTheGuardedService() {
        ADMIN_CONTROLLERS_REACH_NO_PERSISTENCE.check(MAIN);
        ONLY_THE_GUARDED_SERVICE_ENABLES_OR_DISABLES.check(MAIN);
        ONLY_THE_GUARDED_SERVICE_CHANGES_ROLES_OR_DELETES.check(MAIN);
        ONLY_THE_GUARDED_SERVICE_RESETS_FACTORS.check(MAIN);
        // Not vacuous: the admin controller is checked, and the guarded service does make the calls.
        assertThat(MAIN.get(AdminUserController.class).isAnnotatedWith(RestController.class)).isTrue();
        assertThat(MAIN.get(AdminActions.class).getMethodCallsFromSelf())
                .anyMatch(call -> call.getName().equals("setEnabled"))
                .anyMatch(call -> call.getName().equals("setRole"))
                .anyMatch(call -> call.getName().equals("deleteLeavingTombstone"))
                .anyMatch(call -> call.getName().equals("remove")
                        && call.getTargetOwner().isEquivalentTo(TotpFactorRemoval.class));
    }

    /** The ticket's credential-column rule; T-CRED-005 names only the {@code encode()} half. */
    @Test
    void onlyPasswordServiceWritesTheCredentialColumn() {
        ONLY_PASSWORD_SERVICE_WRITES_THE_CREDENTIAL.check(MAIN);
    }

    @Test
    @Proves("T-CRED-009")
    void theResetPathsNeverDependOnTheAuthenticationManager() {
        RESET_PATHS_AVOID_THE_AUTHENTICATION_MANAGER.check(MAIN);
        // Not vacuous: the reset request and redemption live in the checked packages.
        assertThat(MAIN.getPackage("sg.securedhello.passwordreset").getClasses()).isNotEmpty();
    }

    /** ADR-036 names no test-plan row for the cookie repository; T-CSRF-001 covers the response side. */
    @Test
    void mainCodeNeverStoresTheCsrfTokenInACookie() {
        NO_CSRF_COOKIE.check(MAIN);
    }

    @Test
    void testsNeverUseTheCsrfPostProcessor() {
        NO_CSRF_TEST_POST_PROCESSOR.check(TESTS);
    }
}
