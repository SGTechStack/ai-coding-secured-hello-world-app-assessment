package sg.securedhello.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.lang.annotation.Annotation;
import java.time.Clock;
import java.time.InstantSource;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import javax.sql.DataSource;

import jakarta.servlet.http.HttpServletResponse;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.AccessTarget.CodeUnitAccessTarget;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import jakarta.persistence.EntityManager;
import jakarta.servlet.ServletRequest;

import org.slf4j.Logger;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.boot.test.autoconfigure.OverrideAutoConfiguration;
import org.springframework.boot.test.context.filter.annotation.TypeExcludeFilters;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.data.repository.Repository;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Controller;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import sg.securedhello.admin.AdminActions;
import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.mfa.TotpUserDetails;
import sg.securedhello.password.PasswordService;
import sg.securedhello.security.source.SourceKeyResolver;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.time.ClockConfig;
import sg.securedhello.user.UserAccountRepository;
import sg.securedhello.user.PasswordHistoryEntry;
import sg.securedhello.user.PasswordLockoutState;
import sg.securedhello.user.UserAccount;

/**
 * Every architecture rule, in one place. {@link ArchitectureTest} applies them to the code base; add a rule here and
 * a {@code @Proves} method there.
 */
final class ArchitectureRules {

    private ArchitectureRules() {
    }

    /**
     * T-ARCH-001: main code reads time only from the injected {@code Clock} (ADR-066). Every {@code java.time}
     * {@code now(...)} or {@code Chronology.dateNow(...)} other than with a {@code Clock}, the system clock factories
     * ({@code Clock}, {@code InstantSource}) outside {@link ClockConfig}, {@code new Date()},
     * {@code new GregorianCalendar()}, {@code Calendar.getInstance}, {@code System.currentTimeMillis} and {@code System.nanoTime} are
     * banned, whether called or taken as a method reference ({@code Instant::now}).
     */
    static final ArchRule NO_AMBIENT_TIME = noClasses()
            .should().accessTargetWhere(ambientTimeRead())
            .because("time comes only from the injected Clock bean (ADR-066); now(Clock) is allowed");

    /** T-ARCH-001: tests advance the shared clock instead of sleeping (ADR-066). */
    static final ArchRule NO_SLEEP = noClasses()
            .should().callMethodWhere(sleepCall())
            .because("tests advance the shared MutableClock instead of sleeping (ADR-066)");

    /** T-ARCH-006: a class that proves a control boots the full context, never a Boot test slice (ADR-065). */
    static final ArchRule NO_SLICE_ON_PROVING_TESTS = noSliceOnClassesDeclaring(Proves.class);

    /**
     * T-AUTH-011: nothing calls {@code HttpServletResponse.sendError}, which hands the body to the container's error
     * page; every error body goes through {@code ProblemDetailWriter} (ADR-031).
     */
    static final ArchRule NO_SEND_ERROR = noClasses()
            .should().callMethodWhere(sendErrorCall())
            .because("every error body is written by ProblemDetailWriter; sendError is prohibited (ADR-031)");

    /** T-RL-028: only the resolver reads the raw client address; everything else uses the source key (ADR-020). */
    static final ArchRule NO_RAW_CLIENT_ADDRESS = noClasses()
            .that().doNotBelongToAnyOf(SourceKeyResolver.class)
            .should().accessTargetWhere(rawClientAddressRead())
            .because("the client address becomes a source key in SourceKeyResolver only (ADR-020)");

    /**
     * ADR-001: no lockout or limiter branch runs before authentication on whether the account exists. What runs ahead
     * of the provider (the limiter, the login converter) and the pre-authentication checks, which get the account
     * the provider loaded, never look an account up themselves (T-RL-017).
     */
    static final ArchRule NO_ACCOUNT_LOOKUP_BEFORE_AUTHENTICATION = noClasses()
            .that().resideInAPackage("sg.securedhello.security.ratelimit..")
            .or().haveSimpleName("JsonCredentialsConverter")
            .or().haveSimpleName("PreAuthenticationChecks")
            .should().dependOnClassesThat().areAssignableTo(UserAccountRepository.class)
            .because("an account lookup ahead of the provider would put an existence branch before its timing "
                    + "mitigation (ADR-001; R-AUTH-004)");

    /** T-CRED-005: {@code PasswordService} is the only main-code caller of {@code PasswordEncoder.encode()} (ADR-005). */
    static final ArchRule ONLY_PASSWORD_SERVICE_ENCODES = noClasses()
            .that().doNotBelongToAnyOf(PasswordService.class)
            .should().callMethodWhere(encodeCall())
            .because("every password is set through PasswordService, which runs the policy first (ADR-005)");

    /**
     * Only {@code PasswordService} writes the credential column ({@code users.password_hash}) or a retained hash: no
     * other class calls a {@code UserAccount} method or constructor that assigns {@code passwordHash}, or creates a
     * {@code PasswordHistoryEntry} (ADR-005; R-STD-023).
     */
    static final ArchRule ONLY_PASSWORD_SERVICE_WRITES_THE_CREDENTIAL = noClasses()
            .that().doNotBelongToAnyOf(PasswordService.class, UserAccount.class, PasswordHistoryEntry.class)
            .should().callCodeUnitWhere(credentialWrite())
            .because("a password reaches the credential column only through PasswordService's policy (ADR-005)");

    /** The packages the reset-request and redemption paths run through (T-CRED-009). */
    static final String[] RESET_PATH_PACKAGES = {"sg.securedhello.passwordreset..", "sg.securedhello.credential..",
            "sg.securedhello.password..", "sg.securedhello.email.."};

    /**
     * T-CRED-009: the reset paths never depend on the {@code AuthenticationManager}, which refuses a capped password;
     * redemption is the rebinding that clears the cap, so it must not need what the cap blocks (ADR-009; ADR-013).
     */
    static final ArchRule RESET_PATHS_AVOID_THE_AUTHENTICATION_MANAGER =
            noAuthenticationManagerIn(RESET_PATH_PACKAGES);

    /** No class in {@code packageIdentifiers} depends on an {@code AuthenticationManager}. */
    static ArchRule noAuthenticationManagerIn(String... packageIdentifiers) {
        return noClasses()
                .that().resideInAnyPackage(packageIdentifiers)
                .should().dependOnClassesThat().areAssignableTo(AuthenticationManager.class)
                .because("a reset must work while the password authenticator is disabled (ADR-009; ADR-013)");
    }

    /** T-ADM-014: no admin controller reaches persistence directly; admin mutations go through the guard (ADR-048). */
    static final ArchRule ADMIN_CONTROLLERS_REACH_NO_PERSISTENCE =
            controllersReachNoPersistenceIn("sg.securedhello.admin..");

    /** The persistence entry points a controller must not hold: Spring Data, JDBC in each of its forms, and JPA. */
    private static final List<Class<?>> PERSISTENCE_TYPES = List.of(Repository.class, JdbcOperations.class,
            NamedParameterJdbcOperations.class, JdbcClient.class, EntityManager.class, DataSource.class);

    /** No {@code @Controller} or {@code @RestController} in {@code packageIdentifier} reaches persistence. */
    static ArchRule controllersReachNoPersistenceIn(String packageIdentifier) {
        return noClasses()
                .that().resideInAPackage(packageIdentifier).and().areMetaAnnotatedWith(Controller.class)
                .should().dependOnClassesThat(DescribedPredicate.describe("a repository, JDBC or JPA type",
                        type -> PERSISTENCE_TYPES.stream().anyMatch(type::isAssignableTo)))
                .because("every admin mutation reaches persistence through the one guarded service method (ADR-048)");
    }

    /**
     * T-ADM-014: only the guarded service calls the entity's enabled-state setter. It does not see a raw SQL update of
     * {@code users.enabled}; the controller rule and review cover that (ADR-048).
     */
    static final ArchRule ONLY_THE_GUARDED_SERVICE_ENABLES_OR_DISABLES = noClasses()
            .that().doNotBelongToAnyOf(AdminActions.class)
            .should().callMethod(UserAccount.class, "setEnabled", boolean.class)
            .because("an enable or disable runs under AdminActionGuard's lock set and checks (ADR-048)");

    /**
     * Only the guarded service unlocks: clears a factor's tier-1 lock or takes an account's unlocked lockout state
     * (REJ-072). Anything else would be an unaudited unlock path around the closed reason enum (REJ-028; R-LCK-010).
     */
    static final ArchRule ONLY_THE_GUARDED_SERVICE_UNLOCKS = noClasses()
            .that().doNotBelongToAnyOf(AdminActions.class)
            .should().callMethod(TotpUserDetails.class, "unlockTier1")
            .orShould().callMethod(PasswordLockoutState.class, "unlocked")
            .because("an unlock runs under AdminActionGuard's checks and writes its reason to the audit row (REJ-028)");

    /** REJ-008: no remember-me; the browser-session cookie is the only session lifetime on the client. */
    static final ArchRule NO_REMEMBER_ME = noClasses()
            .should().callMethodWhere(rememberMeCall())
            .orShould().dependOnClassesThat()
            .resideInAPackage("org.springframework.security.web.authentication.rememberme..")
            .because("remember-me is an implicit persistent login, prohibited like a cookie Max-Age (REJ-008)");

    /** T-AUD-016: the audit emitter's {@code emit} has no throwable parameter (ADR-055 constraint 3). */
    static final ArchRule AUDIT_EMIT_TAKES_NO_THROWABLE = emitTakesNoThrowable(AuditEmitter.class);

    /** T-AUD-016: no audit code attaches a throwable to a log event (ADR-055 constraint 3). */
    static final ArchRule NO_THROWABLE_ON_AUDIT_ROWS = noThrowableAttachedIn("sg.securedhello.audit..");

    /** Every {@code emit} method {@code emitter} declares takes no {@link Throwable}. */
    static ArchRule emitTakesNoThrowable(Class<?> emitter) {
        return methods()
                .that().areDeclaredIn(emitter).and().haveName("emit")
                .should(new ArchCondition<>("take no Throwable parameter") {
                    @Override
                    public void check(JavaMethod method, ConditionEvents events) {
                        method.getRawParameterTypes().stream()
                                .filter(type -> type.isAssignableTo(Throwable.class))
                                .forEach(type -> events.add(SimpleConditionEvent.violated(method,
                                        method.getFullName() + " takes a " + type.getName())));
                    }
                })
                .because("an exception message can hold request content, and Boot's ECS formatter writes it "
                        + "(ADR-055)");
    }

    /** No class in {@code packageIdentifier} calls {@code setCause} or a logger method taking a throwable. */
    static ArchRule noThrowableAttachedIn(String packageIdentifier) {
        return noClasses()
                .that().resideInAPackage(packageIdentifier)
                .should().callMethodWhere(throwableAttachment())
                .because("no throwable may reach an audit row; throwables stay on the application logger (ADR-055)");
    }

    /** ADR-036: the CSRF token lives in the session, never in a cookie ({@code csrf.spa()} builds on this class). */
    static final ArchRule NO_CSRF_COOKIE = noClasses()
            .should().dependOnClassesThat().areAssignableTo(CookieCsrfTokenRepository.class)
            .because("the CSRF token is session-bound and header-only; no CSRF cookie exists (ADR-036)");

    /**
     * ADR-040: tests fetch a real token ({@code CsrfSession}). Spring Security's {@code csrf()} post-processor swaps
     * the shared context's token repository for one that creates sessions, for every later test in that context.
     */
    static final ArchRule NO_CSRF_TEST_POST_PROCESSOR = noClasses()
            .should().callMethod(SecurityMockMvcRequestPostProcessors.class, "csrf")
            .because("csrf() replaces the session-safe token repository in the shared context (ADR-040)");

    /** No class that declares {@code marker} on itself or on a method may be a Boot test slice. */
    static ArchRule noSliceOnClassesDeclaring(Class<? extends Annotation> marker) {
        return noClasses()
                .that(declare(marker))
                .should(new BootTestSliceCondition())
                .because("security-control tests boot the full context (ADR-065)");
    }

    /** Accesses, including method and constructor references, that read the system clock. */
    private static DescribedPredicate<JavaAccess<?>> ambientTimeRead() {
        return javaTimeNowWithoutAClock()
                .or(systemClockFactoryOutsideClockConfig())
                .or(noArgumentCalendarOrDate())
                .or(calendarGetInstance())
                .or(systemTimeCall());
    }

    /** {@code now()}, {@code now(ZoneId)} or {@code Chronology.dateNow(..)}: anything in java.time but a Clock read. */
    private static DescribedPredicate<JavaAccess<?>> javaTimeNowWithoutAClock() {
        return codeUnitAccess("a java.time now(..) without a Clock", (access, target) ->
                (target.getName().equals("now") || target.getName().equals("dateNow"))
                        && target.getOwner().getPackageName().startsWith("java.time")
                        && !takesOnlyAClock(target));
    }

    private static boolean takesOnlyAClock(CodeUnitAccessTarget target) {
        List<JavaClass> parameters = target.getRawParameterTypes();
        return parameters.size() == 1 && parameters.get(0).isEquivalentTo(Clock.class);
    }

    /** The {@code Clock} and {@code InstantSource} factories that read the system clock; only {@link ClockConfig}. */
    private static final Set<String> SYSTEM_CLOCK_FACTORIES = Set.of("systemUTC", "systemDefaultZone", "system",
            "tickMillis", "tickSeconds", "tickMinutes");

    private static DescribedPredicate<JavaAccess<?>> systemClockFactoryOutsideClockConfig() {
        return codeUnitAccess("a system Clock factory outside ClockConfig", (access, target) ->
                target.getOwner().isAssignableTo(InstantSource.class)
                        && SYSTEM_CLOCK_FACTORIES.contains(target.getName())
                        && !access.getOriginOwner().isEquivalentTo(ClockConfig.class));
    }

    private static DescribedPredicate<JavaAccess<?>> noArgumentCalendarOrDate() {
        return codeUnitAccess("new Date() or a Calendar built from the system clock", (access, target) ->
                target.getName().equals(JavaConstructor.CONSTRUCTOR_NAME)
                        && (target.getOwner().isEquivalentTo(Date.class) && target.getRawParameterTypes().isEmpty()
                                || target.getOwner().isAssignableTo(Calendar.class)
                                        && target.getRawParameterTypes().stream().allMatch(type ->
                                                type.isEquivalentTo(TimeZone.class)
                                                        || type.isEquivalentTo(Locale.class))));
    }

    private static DescribedPredicate<JavaAccess<?>> calendarGetInstance() {
        return codeUnitAccess("Calendar.getInstance", (access, target) ->
                target.getName().equals("getInstance") && target.getOwner().isAssignableTo(Calendar.class));
    }

    private static DescribedPredicate<JavaAccess<?>> systemTimeCall() {
        return codeUnitAccess("System.currentTimeMillis or System.nanoTime", (access, target) ->
                target.getOwner().isEquivalentTo(System.class)
                        && (target.getName().equals("currentTimeMillis") || target.getName().equals("nanoTime")));
    }

    /** A call or a method or constructor reference whose target satisfies {@code test}. */
    private static DescribedPredicate<JavaAccess<?>> codeUnitAccess(String description,
            BiPredicate<JavaAccess<?>, CodeUnitAccessTarget> test) {
        return DescribedPredicate.describe(description, access ->
                access.getTarget() instanceof CodeUnitAccessTarget target && test.test(access, target));
    }

    private static DescribedPredicate<JavaMethodCall> sleepCall() {
        return DescribedPredicate.describe("Thread.sleep or TimeUnit.sleep",
                call -> call.getName().equals("sleep")
                        && (call.getTargetOwner().isEquivalentTo(Thread.class)
                                || call.getTargetOwner().isAssignableTo(TimeUnit.class)));
    }

    private static DescribedPredicate<JavaMethodCall> sendErrorCall() {
        return DescribedPredicate.describe("HttpServletResponse.sendError",
                call -> call.getName().equals("sendError")
                        && call.getTargetOwner().isAssignableTo(HttpServletResponse.class));
    }

    private static DescribedPredicate<JavaAccess<?>> rawClientAddressRead() {
        return DescribedPredicate.describe("ServletRequest.getRemoteAddr or WebAuthenticationDetails.getRemoteAddress",
                access -> access.getName().equals("getRemoteAddr")
                        && access.getTargetOwner().isAssignableTo(ServletRequest.class)
                        || access.getName().equals("getRemoteAddress")
                        && access.getTargetOwner().isAssignableTo(WebAuthenticationDetails.class));
    }

    private static DescribedPredicate<JavaMethodCall> throwableAttachment() {
        return DescribedPredicate.describe("LoggingEventBuilder.setCause or a Logger method taking a Throwable",
                call -> call.getName().equals("setCause")
                        && call.getTargetOwner().isAssignableTo(LoggingEventBuilder.class)
                        || call.getTargetOwner().isAssignableTo(Logger.class)
                        && call.getTarget().getRawParameterTypes().stream()
                        .anyMatch(type -> type.isAssignableTo(Throwable.class)));
    }

    private static DescribedPredicate<JavaMethodCall> encodeCall() {
        return DescribedPredicate.describe("PasswordEncoder.encode",
                call -> call.getName().equals("encode") && call.getTargetOwner().isAssignableTo(PasswordEncoder.class));
    }

    /** A call to a {@code UserAccount} code unit that assigns {@code passwordHash}, or a history-entry constructor. */
    private static DescribedPredicate<JavaCall<?>> credentialWrite() {
        return DescribedPredicate.describe("a write of the credential column or a retained hash",
                call -> call.getTargetOwner().isEquivalentTo(PasswordHistoryEntry.class)
                        && call.getName().equals("<init>")
                        || call.getTargetOwner().isEquivalentTo(UserAccount.class)
                        && call.getTarget().resolveMember().stream()
                        .flatMap(unit -> unit.getFieldAccesses().stream())
                        .anyMatch(access -> access.getAccessType() == JavaFieldAccess.AccessType.SET
                                && access.getTarget().getName().equals("passwordHash")));
    }

    private static DescribedPredicate<JavaMethodCall> rememberMeCall() {
        return DescribedPredicate.describe("HttpSecurity.rememberMe",
                call -> call.getName().equals("rememberMe")
                        && call.getTargetOwner().isAssignableTo(HttpSecurity.class));
    }

    private static DescribedPredicate<JavaClass> declare(Class<? extends Annotation> marker) {
        return DescribedPredicate.describe("declare @" + marker.getSimpleName(),
                javaClass -> javaClass.isAnnotatedWith(marker)
                        || javaClass.getMethods().stream().anyMatch(method -> method.isAnnotatedWith(marker)));
    }

    /**
     * Every Boot test slice ({@code @WebMvcTest}, {@code @DataJpaTest}, {@code @JsonTest}, ...) is meta-annotated with
     * {@link OverrideAutoConfiguration} and {@link TypeExcludeFilters}; {@code @SpringBootTest} is not. The check looks
     * at the class, its superclasses and its enclosing classes, since each of them can carry the slice.
     */
    static final class BootTestSliceCondition extends ArchCondition<JavaClass> {

        BootTestSliceCondition() {
            super("be a Spring Boot test slice");
        }

        @Override
        public void check(JavaClass javaClass, ConditionEvents events) {
            sliceCarrier(javaClass).ifPresent(carrier -> events.add(SimpleConditionEvent
                    .satisfied(javaClass, javaClass.getName() + " runs as a Boot test slice via " + carrier.getName())));
        }

        private static Optional<JavaClass> sliceCarrier(JavaClass javaClass) {
            for (JavaClass candidate = javaClass; candidate != null;
                    candidate = candidate.getEnclosingClass().orElse(null)) {
                Optional<JavaClass> found = Stream
                        .concat(Stream.of(candidate), candidate.getAllRawSuperclasses().stream())
                        .filter(BootTestSliceCondition::isSlice)
                        .findFirst();
                if (found.isPresent()) {
                    return found;
                }
            }
            return Optional.empty();
        }

        private static boolean isSlice(JavaClass javaClass) {
            return javaClass.isMetaAnnotatedWith(OverrideAutoConfiguration.class)
                    || javaClass.isMetaAnnotatedWith(TypeExcludeFilters.class);
        }
    }
}
