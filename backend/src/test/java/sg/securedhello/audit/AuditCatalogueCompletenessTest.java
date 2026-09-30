package sg.securedhello.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import sg.securedhello.testsupport.Proves;

/**
 * Every event the governing standard's §3.3 requires, and every PRD event, maps to at least one catalogue member; the
 * three the standard lists that this application cannot produce are explicit negative entries, each asserting that no
 * endpoint or property exists to produce it (Std §3.3; LOG §3.4; ASVS 16.3.3 (L2)). The generated log inventory lists
 * the same members (T-AUD-017), so the snapshot covers them too.
 */
class AuditCatalogueCompletenessTest {

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("sg.securedhello");

    /** The required events, each with the members that record it. */
    static Stream<Arguments> requiredEvents() {
        return Stream.of(
                Arguments.of("login success", List.of(AuditEvent.LOGIN_SUCCESS)),
                Arguments.of("login failure", List.of(AuditEvent.LOGIN_FAILURE)),
                Arguments.of("lockout triggered", List.of(AuditEvent.LOCKOUT_TRIGGERED, AuditEvent.PASSWORD_DISABLED,
                        AuditEvent.TOTP_FACTOR_LOCKED, AuditEvent.TOTP_FACTOR_DISABLED)),
                Arguments.of("lockout cleared", List.of(AuditEvent.LOCKOUT_CLEARED, AuditEvent.ADMIN_USER_UNLOCKED)),
                Arguments.of("password reset requested", List.of(AuditEvent.PASSWORD_RESET_REQUESTED,
                        AuditEvent.ADMIN_RESET_ISSUED)),
                Arguments.of("password reset completed", List.of(AuditEvent.PASSWORD_RESET_COMPLETED)),
                Arguments.of("role change", List.of(AuditEvent.ADMIN_USER_PROMOTED, AuditEvent.ADMIN_USER_DEMOTED)),
                Arguments.of("account enabled", List.of(AuditEvent.ADMIN_USER_ENABLED)),
                Arguments.of("account disabled", List.of(AuditEvent.ADMIN_USER_DISABLED)),
                Arguments.of("account deleted", List.of(AuditEvent.ADMIN_USER_DELETED)),
                Arguments.of("account created by an administrator", List.of(AuditEvent.ADMIN_USER_INVITED)),
                Arguments.of("second factor reset", List.of(AuditEvent.TOTP_REMOVED)),
                Arguments.of("CSRF rejection", List.of(AuditEvent.CSRF_REJECTED)),
                Arguments.of("failed administrative attempt", List.of(AuditEvent.ADMIN_ACTION_REFUSED)),
                Arguments.of("session start", List.of(AuditEvent.SESSION_START)),
                Arguments.of("logout", List.of(AuditEvent.LOGOUT)),
                Arguments.of("application startup", List.of(AuditEvent.APPLICATION_STARTUP)),
                Arguments.of("application shutdown", List.of(AuditEvent.APPLICATION_SHUTDOWN)));
    }

    /** Admin actions on another account: the row names both the actor and the subject. */
    private static final Set<AuditEvent> ACTOR_AND_SUBJECT = Set.of(AuditEvent.ADMIN_USER_PROMOTED,
            AuditEvent.ADMIN_USER_DEMOTED, AuditEvent.ADMIN_USER_ENABLED, AuditEvent.ADMIN_USER_DISABLED,
            AuditEvent.ADMIN_USER_DELETED, AuditEvent.ADMIN_USER_UNLOCKED, AuditEvent.ADMIN_ACTION_REFUSED,
            AuditEvent.ADMIN_RESET_ISSUED, AuditEvent.ADMIN_USER_INVITED, AuditEvent.TOTP_REMOVED);

    @ParameterizedTest(name = "{0}")
    @MethodSource("requiredEvents")
    @Proves("T-AUD-014")
    void everyRequiredEventHasACatalogueMember(String required, List<AuditEvent> members) {
        assertThat(members).as(required).isNotEmpty().allSatisfy(member -> {
            assertThat(member.definition().message()).isNotBlank();
            if (ACTOR_AND_SUBJECT.contains(member)) {
                assertThat(member.definition().required()).as("%s names actor and subject", member)
                        .contains(AuditKey.USER_ID, AuditKey.USER_TARGET_ID);
            }
        });
    }

    /** The mapping paths every controller method declares, with whether the mapping is a safe read. */
    private static List<Map.Entry<String, Boolean>> mappings() {
        return MAIN.stream().flatMap(javaClass -> javaClass.getMethods().stream())
                .flatMap(AuditCatalogueCompletenessTest::pathsOf).toList();
    }

    private static Stream<Map.Entry<String, Boolean>> pathsOf(JavaMethod method) {
        Stream.Builder<Map.Entry<String, Boolean>> paths = Stream.builder();
        method.tryGetAnnotationOfType(GetMapping.class).ifPresent(a -> Stream.of(a.value())
                .forEach(path -> paths.add(Map.entry(path, true))));
        method.tryGetAnnotationOfType(PostMapping.class).ifPresent(a -> Stream.of(a.value())
                .forEach(path -> paths.add(Map.entry(path, false))));
        method.tryGetAnnotationOfType(PutMapping.class).ifPresent(a -> Stream.of(a.value())
                .forEach(path -> paths.add(Map.entry(path, false))));
        method.tryGetAnnotationOfType(PatchMapping.class).ifPresent(a -> Stream.of(a.value())
                .forEach(path -> paths.add(Map.entry(path, false))));
        method.tryGetAnnotationOfType(DeleteMapping.class).ifPresent(a -> Stream.of(a.value())
                .forEach(path -> paths.add(Map.entry(path, false))));
        method.tryGetAnnotationOfType(RequestMapping.class).ifPresent(a -> Stream.of(a.value())
                .forEach(path -> paths.add(Map.entry(path, false))));
        return paths.build();
    }

    /** Every {@code app.*} property the shipped configuration sets. */
    private static Set<String> applicationProperties() throws IOException {
        Set<String> names = new TreeSet<>();
        for (String file : List.of("application.yml", "application-dev.yml", "application-otlp.yml")) {
            for (PropertySource<?> document : new YamlPropertySourceLoader().load(file, new ClassPathResource(file))) {
                ((Map<?, ?>) document.getSource()).keySet().stream().map(String::valueOf)
                        .filter(name -> name.startsWith("app.")).forEach(names::add);
            }
        }
        return names;
    }

    @Test
    @Proves("T-AUD-014")
    void theThreeNotApplicableEventsHaveNoEndpointOrPropertyToProduceThem() throws IOException {
        List<Map.Entry<String, Boolean>> mappings = mappings();
        assertThat(mappings).as("controller mappings found").hasSizeGreaterThan(15);
        List<String> mutating = mappings.stream().filter(mapping -> !mapping.getValue()).map(Map.Entry::getKey)
                .toList();

        // Security-header configuration change: no route changes a header, and no property configures one.
        assertThat(mutating).noneMatch(path -> path.toLowerCase().contains("header"));
        // Critical configuration change: no route changes configuration at runtime; the actuator is read-only.
        assertThat(mutating).noneMatch(path -> path.toLowerCase().matches(".*(config|setting|propert|env).*"));
        // Bulk export: no route exports data, read or write.
        assertThat(mappings).noneMatch(mapping -> mapping.getKey().toLowerCase().matches(".*(export|download|dump).*"));
        assertThat(applicationProperties()).isNotEmpty()
                .noneMatch(name -> name.matches("app\\.[^.]*header.*|app\\.security\\.headers\\..*|app\\..*export.*"));
    }
}
