package sg.securedhello.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import sg.securedhello.admin.AdminActions;
import sg.securedhello.architecture.fixtures.ArchitectureViolations;
import sg.securedhello.architecture.fixtures.PlaceholderProves;
import sg.securedhello.mfa.TotpFactorRemoval;
import sg.securedhello.password.PasswordService;
import sg.securedhello.security.source.SourceKeyResolver;
import sg.securedhello.time.ClockConfig;
import sg.securedhello.user.PasswordHistoryEntry;
import sg.securedhello.user.UserAccount;

/** Proves each rule actually catches what it bans, and lets through what it allows. */
class ArchitectureRulesSelfTest {

    private static final ArchRule NO_SLICE_ON_FIXTURES = ArchitectureRules.noSliceOnClassesDeclaring(
            PlaceholderProves.class);

    @ParameterizedTest
    @ValueSource(classes = {ArchitectureViolations.ReadsInstantNow.class, ArchitectureViolations.ReadsLocalDateNow.class,
            ArchitectureViolations.ReadsLocalDateTimeNow.class, ArchitectureViolations.ReadsZonedDateTimeNow.class,
            ArchitectureViolations.ReadsOffsetDateTimeNow.class, ArchitectureViolations.ConstructsDate.class,
            ArchitectureViolations.ReadsCurrentTimeMillis.class, ArchitectureViolations.ReadsNanoTime.class,
            ArchitectureViolations.ReadsLocalTimeNow.class, ArchitectureViolations.ReadsYearNow.class,
            ArchitectureViolations.ReadsYearMonthNow.class, ArchitectureViolations.ReadsNowInAZone.class,
            ArchitectureViolations.BuildsSystemUtcClock.class, ArchitectureViolations.BuildsSystemDefaultZoneClock.class,
            ArchitectureViolations.ReadsCalendarInstance.class, ArchitectureViolations.ReferencesInstantNow.class,
            ArchitectureViolations.ReferencesDateConstructor.class, ArchitectureViolations.ConstructsGregorianCalendar.class,
            ArchitectureViolations.ConstructsGregorianCalendarInAZone.class,
            ArchitectureViolations.ReadsChronologyDateNow.class, ArchitectureViolations.BuildsSystemInstantSource.class})
    void ambientTimeIsCaught(Class<?> violator) {
        assertViolates(ArchitectureRules.NO_AMBIENT_TIME, violator);
    }

    @Test
    void timeFromAnInjectedClockIsAllowed() {
        assertPasses(ArchitectureRules.NO_AMBIENT_TIME, ArchitectureViolations.ReadsInjectedClock.class,
                ArchitectureViolations.ReferencesNowWithAClock.class);
    }

    @Test
    void onlyTheClockConfigurationMayBuildTheSystemClock() {
        assertPasses(ArchitectureRules.NO_AMBIENT_TIME, ClockConfig.class);
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

    @Test
    void anEmitTakingAThrowableIsCaught() {
        assertViolates(ArchitectureRules.emitTakesNoThrowable(ArchitectureViolations.EmitsWithThrowable.class),
                ArchitectureViolations.EmitsWithThrowable.class);
    }

    @Test
    void anEmitWithoutAThrowableIsAllowed() {
        assertPasses(ArchitectureRules.AUDIT_EMIT_TAKES_NO_THROWABLE, sg.securedhello.audit.AuditEmitter.class);
    }

    @ParameterizedTest
    @ValueSource(classes = {ArchitectureViolations.AttachesCause.class, ArchitectureViolations.LogsThrowable.class})
    void aThrowableAttachedToALogEventIsCaught(Class<?> violator) {
        assertViolates(ArchitectureRules.noThrowableAttachedIn("sg.securedhello.architecture.fixtures.."), violator);
    }

    @Test
    void loggingWithoutAThrowableIsAllowed() {
        assertPasses(ArchitectureRules.noThrowableAttachedIn("sg.securedhello.architecture.fixtures.."),
                ArchitectureViolations.LogsWithoutThrowable.class);
    }

    @Test
    void aCsrfCookieRepositoryIsCaught() {
        assertViolates(ArchitectureRules.NO_CSRF_COOKIE, ArchitectureViolations.StoresCsrfInACookie.class);
    }

    @Test
    void theCsrfTestPostProcessorIsCaught() {
        assertViolates(ArchitectureRules.NO_CSRF_TEST_POST_PROCESSOR,
                ArchitectureViolations.UsesCsrfPostProcessor.class);
    }

    @Test
    void anAccountLookupAheadOfTheProviderIsCaught() {
        assertViolates(ArchitectureRules.NO_ACCOUNT_LOOKUP_BEFORE_AUTHENTICATION,
                ArchitectureViolations.JsonCredentialsConverter.class);
    }

    @Test
    void anEncodeOutsidePasswordServiceIsCaught() {
        assertViolates(ArchitectureRules.ONLY_PASSWORD_SERVICE_ENCODES, ArchitectureViolations.EncodesAPassword.class);
    }

    @Test
    void passwordServiceMayEncode() {
        assertPasses(ArchitectureRules.ONLY_PASSWORD_SERVICE_ENCODES, PasswordService.class,
                ArchitectureViolations.ReadsTheCredentialColumn.class);
    }

    @ParameterizedTest
    @ValueSource(classes = {ArchitectureViolations.WritesTheCredentialColumn.class,
            ArchitectureViolations.WritesARetainedHash.class})
    void aCredentialWriteOutsidePasswordServiceIsCaught(Class<?> violator) {
        assertViolates(ArchitectureRules.ONLY_PASSWORD_SERVICE_WRITES_THE_CREDENTIAL, violator, UserAccount.class,
                PasswordHistoryEntry.class);
    }

    @Test
    void readingTheCredentialAndPasswordServiceWritingItAreAllowed() {
        assertPasses(ArchitectureRules.ONLY_PASSWORD_SERVICE_WRITES_THE_CREDENTIAL,
                ArchitectureViolations.ReadsTheCredentialColumn.class, PasswordService.class, UserAccount.class,
                PasswordHistoryEntry.class);
    }

    @ParameterizedTest
    @ValueSource(classes = {ArchitectureViolations.ControllerReachesARepository.class,
            ArchitectureViolations.ControllerRunsSql.class, ArchitectureViolations.ControllerUsesJpa.class,
            ArchitectureViolations.PlainControllerUsesJdbcClient.class})
    void aControllerReachingPersistenceIsCaught(Class<?> violator) {
        assertViolates(ArchitectureRules.controllersReachNoPersistenceIn("sg.securedhello.architecture.fixtures.."),
                violator);
    }

    @Test
    void aControllerGoingThroughAServiceIsAllowed() {
        assertPasses(ArchitectureRules.controllersReachNoPersistenceIn("sg.securedhello.architecture.fixtures.."),
                ArchitectureViolations.ControllerUsesAService.class);
    }

    @Test
    void anEnableOrDisableOutsideTheGuardedServiceIsCaught() {
        assertViolates(ArchitectureRules.ONLY_THE_GUARDED_SERVICE_ENABLES_OR_DISABLES,
                ArchitectureViolations.DisablesAroundTheGuard.class, UserAccount.class);
        assertPasses(ArchitectureRules.ONLY_THE_GUARDED_SERVICE_ENABLES_OR_DISABLES, AdminActions.class,
                UserAccount.class);
    }

    @Test
    void aFactorResetOutsideTheGuardedServiceIsCaught() {
        assertViolates(ArchitectureRules.ONLY_THE_GUARDED_SERVICE_RESETS_FACTORS,
                ArchitectureViolations.ResetsAFactorAroundTheGuard.class, TotpFactorRemoval.class);
        assertPasses(ArchitectureRules.ONLY_THE_GUARDED_SERVICE_RESETS_FACTORS, AdminActions.class,
                TotpFactorRemoval.class);
    }

    @Test
    void anAuthenticationManagerOnTheResetPathIsCaught() {
        assertViolates(ArchitectureRules.noAuthenticationManagerIn("sg.securedhello.architecture.fixtures.."),
                ArchitectureViolations.AuthenticatesOnTheResetPath.class);
        assertPasses(ArchitectureRules.noAuthenticationManagerIn("sg.securedhello.architecture.fixtures.."),
                ArchitectureViolations.EncodesAPassword.class);
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
