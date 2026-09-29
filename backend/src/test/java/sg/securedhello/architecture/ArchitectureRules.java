package sg.securedhello.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.lang.annotation.Annotation;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import jakarta.servlet.http.HttpServletResponse;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import jakarta.servlet.ServletRequest;

import org.slf4j.Logger;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.boot.test.autoconfigure.OverrideAutoConfiguration;
import org.springframework.boot.test.context.filter.annotation.TypeExcludeFilters;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.password.PasswordService;
import sg.securedhello.security.source.SourceKeyResolver;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.user.UserAccountRepository;
import sg.securedhello.user.PasswordHistoryEntry;
import sg.securedhello.user.UserAccount;

/**
 * Every architecture rule, in one place. {@link ArchitectureTest} applies them to the code base; add a rule here and
 * a {@code @Proves} method there.
 */
final class ArchitectureRules {

    private ArchitectureRules() {
    }

    /** T-ARCH-001: main code reads time only from the injected {@code Clock} (ADR-066). */
    static final ArchRule NO_AMBIENT_TIME = noClasses()
            .should().callMethod(Instant.class, "now")
            .orShould().callMethod(LocalDate.class, "now")
            .orShould().callMethod(LocalDateTime.class, "now")
            .orShould().callMethod(ZonedDateTime.class, "now")
            .orShould().callMethod(OffsetDateTime.class, "now")
            .orShould().callConstructor(Date.class)
            .orShould().callMethod(System.class, "currentTimeMillis")
            .orShould().callMethod(System.class, "nanoTime")
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
