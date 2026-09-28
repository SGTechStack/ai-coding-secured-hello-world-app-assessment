package sg.securedhello.architecture;

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

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import org.springframework.boot.test.autoconfigure.OverrideAutoConfiguration;
import org.springframework.boot.test.context.filter.annotation.TypeExcludeFilters;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import sg.securedhello.testsupport.Proves;

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

    /** REJ-008: no remember-me; the browser-session cookie is the only session lifetime on the client. */
    static final ArchRule NO_REMEMBER_ME = noClasses()
            .should().callMethodWhere(rememberMeCall())
            .orShould().dependOnClassesThat()
            .resideInAPackage("org.springframework.security.web.authentication.rememberme..")
            .because("remember-me is an implicit persistent login, prohibited like a cookie Max-Age (REJ-008)");

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
