package sg.securedhello.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import sg.securedhello.architecture.fixtures.ArchitectureViolations;
import sg.securedhello.architecture.fixtures.PlaceholderProves;
import sg.securedhello.security.source.SourceKeyResolver;

/** Proves each rule actually catches what it bans, and lets through what it allows. */
class ArchitectureRulesSelfTest {

    private static final ArchRule NO_SLICE_ON_FIXTURES = ArchitectureRules.noSliceOnClassesDeclaring(
            PlaceholderProves.class);

    @ParameterizedTest
    @ValueSource(classes = {ArchitectureViolations.ReadsInstantNow.class, ArchitectureViolations.ReadsLocalDateNow.class,
            ArchitectureViolations.ReadsLocalDateTimeNow.class, ArchitectureViolations.ReadsZonedDateTimeNow.class,
            ArchitectureViolations.ReadsOffsetDateTimeNow.class, ArchitectureViolations.ConstructsDate.class,
            ArchitectureViolations.ReadsCurrentTimeMillis.class, ArchitectureViolations.ReadsNanoTime.class})
    void ambientTimeIsCaught(Class<?> violator) {
        assertViolates(ArchitectureRules.NO_AMBIENT_TIME, violator);
    }

    @Test
    void timeFromAnInjectedClockIsAllowed() {
        assertPasses(ArchitectureRules.NO_AMBIENT_TIME, ArchitectureViolations.ReadsInjectedClock.class);
    }

    @ParameterizedTest
    @ValueSource(classes = {ArchitectureViolations.SleepsOnThread.class, ArchitectureViolations.SleepsOnTimeUnit.class})
    void sleepingIsCaught(Class<?> violator) {
        assertViolates(ArchitectureRules.NO_SLEEP, violator);
    }

    @ParameterizedTest
    @ValueSource(classes = {ArchitectureViolations.SendsErrorWithStatus.class,
            ArchitectureViolations.SendsErrorWithMessage.class, ArchitectureViolations.SendsErrorOnAWrapper.class})
    void sendErrorIsCaught(Class<?> violator) {
        assertViolates(ArchitectureRules.NO_SEND_ERROR, violator);
    }

    @Test
    void settingAStatusIsAllowed() {
        assertPasses(ArchitectureRules.NO_SEND_ERROR, ArchitectureViolations.SetsStatus.class);
    }

    @ParameterizedTest
    @ValueSource(classes = {ArchitectureViolations.ReadsHttpRemoteAddr.class,
            ArchitectureViolations.ReadsServletRemoteAddr.class, ArchitectureViolations.ReferencesRemoteAddr.class,
            ArchitectureViolations.ReadsDetailsRemoteAddress.class})
    void rawClientAddressReadsAreCaught(Class<?> violator) {
        assertViolates(ArchitectureRules.NO_RAW_CLIENT_ADDRESS, violator);
    }

    @Test
    void theSourceKeyResolverMayReadTheRawClientAddress() {
        assertPasses(ArchitectureRules.NO_RAW_CLIENT_ADDRESS, SourceKeyResolver.class,
                ArchitectureViolations.ReadsInjectedClock.class);
    }

    @Test
    void aProvingSliceIsCaught() {
        assertViolates(NO_SLICE_ON_FIXTURES, ArchitectureViolations.ProvingWebMvcSlice.class);
    }

    @Test
    void aSliceInheritedFromASuperclassIsCaught() {
        assertViolates(NO_SLICE_ON_FIXTURES, ArchitectureViolations.ProvingSubclassOfSlice.class,
                ArchitectureViolations.ProvingWebMvcSliceBase.class);
    }

    @Test
    void aSliceOnAnEnclosingClassIsCaught() {
        assertViolates(NO_SLICE_ON_FIXTURES, ArchitectureViolations.OuterSlice.InnerProving.class,
                ArchitectureViolations.OuterSlice.class);
    }

    @Test
    void nonProvingSlicesAndProvingFullContextsAreAllowed() {
        assertPasses(NO_SLICE_ON_FIXTURES, ArchitectureViolations.NonProvingSlice.class,
                ArchitectureViolations.ProvingFullContext.class);
    }

    @Test
    void rememberMeIsCaught() {
        assertViolates(ArchitectureRules.NO_REMEMBER_ME, ArchitectureViolations.EnablesRememberMe.class);
    }

    private static void assertViolates(ArchRule rule, Class<?>... classes) {
        assertThat(rule.evaluate(importClasses(classes)).hasViolation()).as("%s violates the rule", classes[0]).isTrue();
    }

    private static void assertPasses(ArchRule rule, Class<?>... classes) {
        assertThat(rule.evaluate(importClasses(classes)).getFailureReport().getDetails()).isEmpty();
    }

    private static JavaClasses importClasses(Class<?>... classes) {
        return new ClassFileImporter().importClasses(classes);
    }
}
