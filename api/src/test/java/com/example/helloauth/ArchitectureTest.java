package com.example.helloauth;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

/**
 * Architecture rules, enforced rather than described.
 *
 * <p>A flat-layered design, which is only a design for as long as something checks. These rules are
 * the check, and each one exists because breaking it would cost something concrete — noted per rule
 * rather than left as "good practice".
 */
@AnalyzeClasses(
        packages = "com.example.helloauth",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /**
     * The layering itself.
     *
     * <p>{@code settings} and {@code domain} are open to everyone by design: configuration values and
     * the entities are what the layers are all about, and pretending otherwise would just mean
     * duplicating both. {@code config} is closed to everyone, which is the rule with teeth — it keeps
     * framework wiring from becoming a back door through which any layer can reach any other.
     */
    @ArchTest
    static final ArchRule layers_are_respected =
            layeredArchitecture()
                    .consideringOnlyDependenciesInLayers()
                    .layer("Settings")
                    .definedBy("..settings..")
                    .layer("Domain")
                    .definedBy("..domain..")
                    .layer("Repository")
                    .definedBy("..repository..")
                    .layer("Service")
                    .definedBy("..service..")
                    .layer("Web")
                    .definedBy("..web..")
                    .layer("Config")
                    .definedBy("..config..")
                    .whereLayer("Config")
                    .mayNotBeAccessedByAnyLayer()
                    .whereLayer("Web")
                    .mayOnlyBeAccessedByLayers("Config")
                    .whereLayer("Service")
                    .mayOnlyBeAccessedByLayers("Web", "Config")
                    .whereLayer("Repository")
                    .mayOnlyBeAccessedByLayers("Service", "Config");

    /** A cycle between packages means neither can be understood, changed or tested on its own. */
    @ArchTest
    static final ArchRule packages_are_acyclic =
            SlicesRuleDefinition.slices()
                    .matching("com.example.helloauth.(*)..")
                    .should()
                    .beFreeOfCycles();

    /**
     * The entities must not reach into the layers that use them. An entity that knows about a
     * repository is one step from loading data in a getter, which is how a list endpoint turns into a
     * few hundred queries without anyone writing a query.
     */
    @ArchTest
    static final ArchRule domain_does_not_depend_on_the_application =
            noClasses()
                    .that()
                    .resideInAPackage("..domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..repository..", "..service..", "..web..", "..config..");

    /**
     * Only one service may touch the servlet API, and it is named for exactly that.
     *
     * <p>Starting a session is inseparable from the request it happens on, so some class has to hold
     * that coupling. Confining it to one keeps the rest of the service layer callable from anywhere —
     * a scheduled job, a message handler, a test — instead of only from inside an HTTP request.
     */
    @ArchTest
    static final ArchRule only_the_session_service_touches_the_servlet_api =
            noClasses()
                    .that()
                    .resideInAPackage("..service..")
                    .and()
                    .doNotHaveSimpleName("SessionService")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("jakarta.servlet..");

    /**
     * Services must not know about HTTP status codes or request mappings. The mapping from a failure
     * to a status code lives in one place in the web layer, which is what makes it possible to check
     * that two responses which must be indistinguishable still are.
     */
    @ArchTest
    static final ArchRule services_are_transport_agnostic =
            noClasses()
                    .that()
                    .resideInAPackage("..service..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("org.springframework.web..", "org.springframework.http..");

    /**
     * Password hashing stays out of the web layer. A controller that can hash is a controller that
     * can be given a second, subtly different hashing path, and the PRD allows exactly one.
     */
    @ArchTest
    static final ArchRule only_services_hash_passwords =
            noClasses()
                    .that()
                    .resideInAnyPackage("..web..", "..repository..", "..domain..")
                    .should()
                    .dependOnClassesThat()
                    .haveNameMatching(".*PasswordEncoder")
                    .as("only the service layer should use a PasswordEncoder");

    /**
     * No response type may read a password hash. The DTOs are the last gate before bytes leave the
     * process, so this is the cheapest possible place to make "hashes never leave the server" a fact
     * rather than an intention.
     */
    @ArchTest
    static final ArchRule response_payloads_never_read_a_password_hash =
            noClasses()
                    .that()
                    .resideInAPackage("..web.dto..")
                    .should()
                    .callMethodWhere(
                            com.tngtech.archunit.core.domain.JavaCall.Predicates.target(
                                    com.tngtech.archunit.core.domain.properties.HasName.Predicates
                                            .name("getPasswordHash")))
                    .as("web DTOs should never read a password hash");

    /**
     * Constructor injection only. Field injection hides a class's real dependencies from anyone
     * reading its constructor, and quietly permits circular wiring that constructor injection would
     * have failed on at startup.
     */
    @ArchTest
    static final ArchRule no_field_injection =
            fields()
                    .should()
                    .notBeAnnotatedWith(org.springframework.beans.factory.annotation.Autowired.class)
                    .as("dependencies should be injected through constructors");

    /** Log lines are searchable and levelled; {@code System.out} is neither. */
    @ArchTest
    static final ArchRule nothing_writes_to_standard_streams =
            noClasses()
                    .should()
                    .accessField(System.class, "out")
                    .orShould()
                    .accessField(System.class, "err")
                    .as("logging should go through SLF4J, not the standard streams");
}
