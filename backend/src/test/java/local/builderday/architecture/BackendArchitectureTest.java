package local.builderday.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import local.builderday.account.core.model.AccountPrincipal;
import local.builderday.account.core.repository.UserRepository;
import local.builderday.account.core.service.UserProfileService;
import local.builderday.architecture.fixture.core.CoreSide;
import local.builderday.architecture.fixture.model.SpringDependentModel;
import local.builderday.architecture.fixture.usecase.UseCaseSide;
import org.junit.jupiter.api.Test;

/**
 * Enforces the domain boundaries of ADR 0006 (docs/adr/0006-domain-first-structure.md), the core-and-use-case layout
 * of ADR 0008 (docs/adr/0008-uniform-domain-core.md) and the naming and layering rules of ADR 0002.
 */
@AnalyzeClasses(packages = "local.builderday", importOptions = ImportOption.DoNotIncludeTests.class)
class BackendArchitectureTest {

  /** Top-level domains (auth, account, notification) and common. */
  @ArchTest
  static final ArchRule domainSlicesAreFreeOfCycles = slices().matching("local.builderday.(*)..").should()
      .beFreeOfCycles();

  /**
   * Inside each domain: use cases depend only on themselves and the domain's core; the core on no use case. A domain
   * without use cases may skip core (ADR 0008 amendment); this rule then fails its first use case until it adds one.
   */
  @ArchTest
  static final ArchRule useCasesDependOnlyOnTheirDomainCore = classes().that(resideInAPackage("local.builderday.*.*.."))
      .and(not(resideInAPackage("local.builderday.common..")))
      .should(dependWithinTheirDomainOnlyOnThemselvesOrTheCore("local.builderday"));

  /**
   * Every domain class sits in its {@code core}, a use case or (no use cases yet) a layer folder, never directly in
   * the domain package.
   */
  @ArchTest
  static final ArchRule domainPackagesHoldOnlyCoreAndUseCases = noClasses()
      .should().resideInAnyPackage(
          "local.builderday.auth", "local.builderday.account", "local.builderday.notification");

  // Extend this list whenever a new domain package is added.
  @ArchTest
  static final ArchRule commonDependsOnNoDomain = noClasses().that().resideInAPackage("local.builderday.common..")
      .should().dependOnClassesThat().resideInAnyPackage(
          "local.builderday.auth..", "local.builderday.account..", "local.builderday.notification..");

  /** The Greeting uses nothing of the application but common (Spring Security supplies the principal). */
  @ArchTest
  static final ArchRule greetingDependsOnlyOnCommon = noClasses()
      .that().resideInAPackage("local.builderday.account.greeting..")
      .should().dependOnClassesThat(resideInAPackage("local.builderday..")
          .and(not(resideInAnyPackage("local.builderday.account.greeting..", "local.builderday.common.."))));

  @ArchTest
  static final ArchRule repositoriesAreAccessedOnlyByTheirOwnFeature = classes()
      .that().resideInAPackage("..repository..")
      .should(beAccessedOnlyFromOwnFeature());

  @ArchTest
  static final ArchRule controllersDoNotAccessRepositories = noClasses().that().resideInAPackage("..controller..")
      .should().dependOnClassesThat().resideInAPackage("..repository..");

  /**
   * The single named exception is {@link AccountPrincipal}, the Spring Security principal carrying the Account's id
   * (backend steering, Architecture rule 8).
   */
  @ArchTest
  static final ArchRule modelsStayFrameworkFree = noClasses().that().resideInAPackage("..model..")
      .and().doNotHaveFullyQualifiedName(AccountPrincipal.class.getName())
      .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "org.springframework..",
          "..controller.dto..", "..service.dto..", "..repository..");

  /**
   * ADR 0011: a persistence entity holds state only. Its public methods are getters, setters and framework-contract
   * methods (e.g. {@code Persistable.isNew()}, JPA lifecycle callbacks); state-transition behaviour lives on the
   * domain model in {@code model/}. {@code RateLimitBucketEntity.isNew()} is the carve-out this allows.
   */
  @ArchTest
  static final ArchRule entitiesHoldStateOnly = classes().that().resideInAPackage("..repository.entity..")
      .should(declareOnlyGettersSettersAndFrameworkContract());

  /**
   * A repository or service field says what it is, e.g. {@code userProfileService}, never a bare noun like
   * {@code users}.
   */
  @ArchTest
  static final ArchRule repositoryFieldsAreNamedAsRepositories = fields()
      .that().haveRawType(simpleNameEndingWith("Repository"))
      .and().haveNameNotMatching(".*\\$.*")
      .should().haveNameMatching("(.*R|r)epository");

  @ArchTest
  static final ArchRule serviceFieldsAreNamedAsServices = fields().that().haveRawType(simpleNameEndingWith("Service"))
      .and().haveNameNotMatching(".*\\$.*") // compiler-generated, e.g. an inner class's this$0
      .should().haveNameMatching("(.*S|s)ervice");

  /** The same naming rules for test code, minus this package's deliberately bad fixtures. */
  @Test
  void should_nameRepositoryAndServiceFieldsAfterTheirType_when_declaredInTests() {
    var tests = new ClassFileImporter().withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
        .withImportOption(location -> !location.contains("/local/builderday/architecture/"))
        .importPackages("local.builderday");

    repositoryFieldsAreNamedAsRepositories.check(tests);
    serviceFieldsAreNamedAsServices.check(tests);
  }

  @Test
  void should_reportViolation_when_aRepositoryOrServiceFieldIsNamedAfterItsData() {
    var classes = new ClassFileImporter().importClasses(BareNounFields.class);

    assertThat(repositoryFieldsAreNamedAsRepositories.evaluate(classes).hasViolation()).isTrue();
    assertThat(serviceFieldsAreNamedAsServices.evaluate(classes).hasViolation()).isTrue();
  }

  @Test
  void should_reportViolation_when_aModelOtherThanTheAccountPrincipalDependsOnSpring() {
    var classes = new ClassFileImporter().importClasses(SpringDependentModel.class, AccountPrincipal.class);

    var result = modelsStayFrameworkFree.evaluate(classes);

    assertThat(result.getFailureReport().getDetails()).isNotEmpty()
        .allMatch(detail -> detail.contains(SpringDependentModel.class.getName()));
  }

  @Test
  void should_reportViolation_when_anotherFeatureAccessesARepository() {
    var classes = new ClassFileImporter().importClasses(CrossFeatureRepositoryAccess.class, UserRepository.class);

    assertThat(repositoriesAreAccessedOnlyByTheirOwnFeature.evaluate(classes).hasViolation()).isTrue();
  }

  @Test
  void should_reportViolation_when_aDomainCoreDependsOnOneOfItsUseCases() {
    var classes = new ClassFileImporter().importClasses(CoreSide.class, UseCaseSide.class);
    var condition = dependWithinTheirDomainOnlyOnThemselvesOrTheCore("local.builderday.architecture");

    var result = classes().should(condition).evaluate(classes);

    // Only the core's dependency on the use case is a violation; the use case may depend on its core.
    assertThat(result.getFailureReport().getDetails()).isNotEmpty()
        .allMatch(detail -> detail.startsWith("Field <" + CoreSide.class.getName()));
  }

  /**
   * Below {@code root}, the first package segment is the domain and the second its slice: {@code core} or a use case.
   * Within its own domain a class may depend only on its own slice or on {@code core}; {@code core} only on itself.
   */
  private static ArchCondition<JavaClass> dependWithinTheirDomainOnlyOnThemselvesOrTheCore(String root) {
    return new ArchCondition<>("depend within their domain only on their own slice or the domain core") {
      @Override
      public void check(JavaClass origin, ConditionEvents events) {
        String[] from = segmentsBelow(root, origin);
        origin.getDirectDependenciesFromSelf().forEach(dependency -> {
          String[] to = segmentsBelow(root, dependency.getTargetClass());
          boolean sameDomain = from.length > 1 && to.length > 1 && from[0].equals(to[0]);
          boolean allowed = !sameDomain || to[1].equals(from[1]) || to[1].equals("core");
          if (!allowed) {
            events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
          }
        });
      }
    };
  }

  private static String[] segmentsBelow(String root, JavaClass javaClass) {
    String pkg = javaClass.getPackageName();
    return pkg.startsWith(root + ".") ? pkg.substring(root.length() + 1).split("\\.") : new String[0];
  }

  /**
   * Second package segment below {@code local.builderday}, e.g. {@code account} for
   * {@code local.builderday.account.core.service}.
   */
  private static String feature(JavaClass javaClass) {
    String[] segments = javaClass.getPackageName().split("\\.");
    return segments.length > 2 ? segments[2] : "";
  }

  /**
   * A persistence entity may declare only getters ({@code get*}/{@code is*}), setters ({@code set*}) and
   * framework-contract methods; anything else is state-transition behaviour that belongs on the domain model (ADR
   * 0011). Framework-contract methods are {@code Persistable} members ({@code isNew}, {@code getId}) and JPA lifecycle
   * callbacks ({@code @PrePersist} and the like).
   */
  private static ArchCondition<JavaClass> declareOnlyGettersSettersAndFrameworkContract() {
    return new ArchCondition<>("declare only getters, setters and framework-contract methods") {
      @Override
      public void check(JavaClass entity, ConditionEvents events) {
        entity.getMethods().stream()
            .filter(method -> method.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.PUBLIC))
            .filter(method -> !isGetterSetterOrFrameworkContract(method))
            .forEach(method -> events.add(SimpleConditionEvent.violated(method,
                method.getFullName() + " is behaviour; an entity holds state only (ADR 0011)")));
      }
    };
  }

  private static boolean isGetterSetterOrFrameworkContract(com.tngtech.archunit.core.domain.JavaMethod method) {
    String name = method.getName();
    boolean accessor = (name.startsWith("get") || name.startsWith("is")) && method.getRawParameterTypes().isEmpty();
    boolean setter = name.startsWith("set") && method.getRawParameterTypes().size() == 1;
    boolean persistable = name.equals("isNew") && method.getRawParameterTypes().isEmpty();
    boolean lifecycleCallback = method.getAnnotations().stream().anyMatch(annotation ->
        annotation.getRawType().getPackageName().equals("jakarta.persistence"));
    return accessor || setter || persistable || lifecycleCallback;
  }

  private static ArchCondition<JavaClass> beAccessedOnlyFromOwnFeature() {
    return new ArchCondition<>("be accessed only from their own feature") {
      @Override
      public void check(JavaClass target, ConditionEvents events) {
        target.getDirectDependenciesToSelf().stream()
            .filter(dependency -> !feature(dependency.getOriginClass()).equals(feature(target)))
            .forEach(dependency -> events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription())));
      }
    };
  }

  private static DescribedPredicate<JavaClass> simpleNameEndingWith(String suffix) {
    return DescribedPredicate.describe("a type named *" + suffix, type -> type.getSimpleName().endsWith(suffix));
  }

  /** Fixture naming a repository and a service after the data they hold. */
  @SuppressWarnings("unused")
  static class BareNounFields {
    private UserRepository users;
    private UserProfileService profiles;
  }

  /** Fixture in the {@code architecture} test package reaching into the {@code account} domain's repository. */
  @SuppressWarnings("unused")
  static class CrossFeatureRepositoryAccess {
    private UserRepository repository;
  }

  /** Fixture entity carrying state-transition behaviour, which the ADR 0011 rule must reject. Deliberately not a real
   * {@code @Entity} so Hibernate never tries to map it; the rule inspects method shape, not JPA mapping. */
  @SuppressWarnings("unused")
  static class EntityWithBehaviour {
    private java.util.UUID id;
    private boolean enabled;

    public java.util.UUID getId() { return id; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /** Behaviour: this belongs on the domain model, not the entity. */
    public void disable() { this.enabled = false; }
  }

  @Test
  void should_reportViolation_when_anEntityDeclaresBehaviour() {
    var classes = new ClassFileImporter().importClasses(EntityWithBehaviour.class);
    var rule = classes().should(declareOnlyGettersSettersAndFrameworkContract());

    var result = rule.evaluate(classes);

    assertThat(result.hasViolation()).isTrue();
    assertThat(result.getFailureReport().getDetails()).anyMatch(detail -> detail.contains("disable"));
  }
}
