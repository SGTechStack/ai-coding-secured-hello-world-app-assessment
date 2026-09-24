
# ArchUnit Test Writer

## When to use ArchUnit

ArchUnit is the right tool for **structural rules about the compiled Java type system**: which packages may depend on which, naming conventions for classes, required annotations on types or methods, interface implementation mandates, and cycle detection between packages. It operates on bytecode, so it catches things regardless of how code was formatted.

---

## DSL entry points

From `com.tngtech.archunit.lang.ArchRuleDefinition` (and `noX()` negations):

| Entry point | Targets |
|---|---|
| `classes()` / `noClasses()` | All Java classes/interfaces/enums |
| `methods()` / `noMethods()` | Methods |
| `fields()` / `noFields()` | Fields |
| `members()` / `noMembers()` | Methods + fields + constructors |
| `codeUnits()` / `noCodeUnits()` | Methods + constructors |
| `constructors()` / `noConstructors()` | Constructors |

---

## Predicate chaining (after `.that()`)

### Package predicates

| Predicate | Notes |
|---|---|
| `resideInAPackage("..service..")` | `..` = any depth wildcard; `(*)` = capture one segment. **Use `..pkg..` for dependency/access rules** (you want all sub-packages). **Use `..pkg` (no trailing `..`) for naming-convention rules** — `..service..` also matches `service.dto`, `service.impl`, etc., catching non-service classes. |
| `resideInAnyPackage("..a..", "..b..")` | Matches any of the listed packages |
| `resideOutsideOfPackage("..internal..")` | Negated package match |
| `resideOutsideOfPackages("..a..", "..b..")` | Negated multi-package match |

### Naming predicates

| Predicate | Notes |
|---|---|
| `haveSimpleName("Foo")` | Exact unqualified name |
| `haveSimpleNameStartingWith("Foo")` | Prefix match on unqualified name |
| `haveSimpleNameEndingWith("Suffix")` | Suffix match on unqualified name |
| `haveSimpleNameContaining("Fragment")` | Substring match on unqualified name |
| `haveSimpleNameMatching(".*Regex")` | Regex on unqualified name — **`.that()` only**; not available after `.should()`. Use `haveNameMatching` in conditions. |
| `haveNameMatching(".*Regex")` | Full regex on qualified name |
| `haveFullyQualifiedName("com.example.Foo")` | Exact qualified name |
| `haveNameNotMatching(".*Regex")` | Negated regex |

### Type / inheritance predicates

| Predicate | Notes |
|---|---|
| `implement(Interface.class)` | Directly or transitively implements |
| `areAssignableTo(Type.class)` | Broader assignability (includes subclasses) |
| `areAssignableFrom(Type.class)` | Type is assignable from target |
| `areInterfaces()` / `areNotInterfaces()` | Interface check |
| `areEnums()` / `areNotEnums()` | Enum check |
| `areAnnotations()` / `areNotAnnotations()` | Annotation type check |
| `areInnerClasses()` / `areNotInnerClasses()` | Nested class check |
| `areTopLevelClasses()` | Not nested |

### Annotation predicates

| Predicate | Notes |
|---|---|
| `areAnnotatedWith(Ann.class)` | Has annotation (direct or meta) |
| `areNotAnnotatedWith(Ann.class)` | Lacks annotation |
| `areMetaAnnotatedWith(Ann.class)` | Has meta-annotation |

### Visibility predicates

| Predicate | Notes |
|---|---|
| `arePublic()` / `areNotPublic()` | Public visibility |
| `arePrivate()` / `areNotPrivate()` | Private visibility |
| `areProtected()` / `areNotProtected()` | Protected visibility |
| `arePackagePrivate()` / `areNotPackagePrivate()` | Package-private visibility |

### Member-specific predicates (methods/fields/constructors)

| Predicate | Notes |
|---|---|
| `areDeclaredInClassesThat()` | Chain class predicates on declaring class |
| `haveRawReturnType(Type.class)` | Return type match (methods) |
| `haveRawParameterTypes(Type.class, ...)` | Parameter types match (methods/constructors) |
| `areStatic()` / `areNotStatic()` | Static modifier |
| `areFinal()` / `areNotFinal()` | Final modifier |

### Combining predicates

```java
.and()   // logical AND of predicates
.or()    // logical OR of predicates
```

---

## Condition chaining (after `.should()`)

### Dependency / access conditions

| Condition | Notes |
|---|---|
| `onlyBeAccessed().byAnyPackage("..pkg..")` | Restrict inbound access |
| `onlyHaveDependentClassesThat().resideInAnyPackage(...)` | Inbound dependency constraint |
| `onlyDependOnClassesThat().resideInAnyPackage("..a..", "..b..")` | Restrict outbound deps |
| `notDependOnClassesThat().resideInAPackage("..forbidden..")` | Forbidden outbound dep |
| `dependOnClassesThat().areAnnotatedWith(Ann.class)` | Conditional dep rule |
| `onlyAccessClassesThat().resideInAPackage("..allowed..")` | Fine-grained access rule |

### Naming / type conditions

| Condition | Notes |
|---|---|
| `haveSimpleNameEndingWith("Suffix")` | Naming condition |
| `haveSimpleNameStartingWith("Prefix")` | Prefix naming condition |
| `haveNameMatching(".*Regex")` | Regex naming condition |
| `resideInAPackage("com.foo")` | Package placement condition |
| `implement(Interface.class)` | Required interface |
| `beAssignableTo(Type.class)` | Assignability condition |

### Annotation conditions

| Condition | Notes |
|---|---|
| `beAnnotatedWith(Ann.class)` | Required annotation |
| `notBeAnnotatedWith(Ann.class)` | Forbidden annotation |

### Field access conditions

| Condition | Notes |
|---|---|
| `accessField(Owner.class, "fieldName")` | Must access specific field |
| `onlyAccessFieldsThat().areAnnotatedWith(Ann.class)` | Restrict field access |

### Compound conditions

```java
.andShould()   // chain additional should() conditions (AND)
.orShould()    // chain additional should() conditions (OR)
```

---

## Architecture styles (Library API)

### Layered architecture

Two scoping variants exist. Pick the right one based on whether the rule is global or per-module.

#### `consideringAllDependencies()` — global, single hierarchy

Use **only** when there is exactly one controller/service/repository package hierarchy for the whole application. This mode evaluates constraints against every class in the scanned set, so any class anywhere in the codebase that touches a defined layer will be checked. In a multi-module app this will flag legitimate cross-module service calls as violations.

```java
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

// NOTE: layer definitions use "..pkg.." (with trailing ..) intentionally —
// they must capture all classes *under* the layer package, including sub-packages.
// The trailing ".." pitfall only applies to naming-convention `that()` clauses.
layeredArchitecture().consideringAllDependencies()
    .layer("Controller").definedBy("..controller..")
    .layer("Service").definedBy("..service..")
    .layer("Persistence").definedBy("..persistence..")
    .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
    .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller")
    .whereLayer("Persistence").mayOnlyBeAccessedByLayers("Service")
```

#### `consideringOnlyDependenciesInAnyPackage()` — intra-module, multi-module apps

Use when enforcing the controller→service→repository order **inside a single module**. Pass the module's root package as the argument. This restricts evaluation to dependencies where at least one end resides in the given package, so cross-module callers (e.g. another module's controller legitimately calling this module's service) are invisible to the rule and will not be flagged.

Emit one rule per module. Always add `.allowEmptyShould(true)` — some modules may not have all three layers.

```java
// Enforces layering only within com.example.order — cross-module callers are ignored.
layeredArchitecture()
    .consideringOnlyDependenciesInAnyPackage("com.example.order..")
    .layer("Controller").definedBy("com.example.order.controller..")
    .layer("Service").definedBy("com.example.order.service..")
    .layer("Repository").definedBy("com.example.order.repository..")
    .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
    .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller")
    .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service")
    .allowEmptyShould(true)
    .because("No controller-to-repository shortcuts within the order module");
```

### Onion / Hexagonal architecture

```java
import static com.tngtech.archunit.library.Architectures.onionArchitecture;

onionArchitecture()
    .domainModels("com.myapp.domain.model..")
    .domainServices("com.myapp.domain.service..")
    .applicationServices("com.myapp.application..")
    .adapter("cli", "com.myapp.adapter.cli..")
    .adapter("persistence", "com.myapp.adapter.persistence..")
    .adapter("rest", "com.myapp.adapter.rest..")
```

Key constraints enforced automatically:
- Domain packages (models + services) form the inner core
- Application services may use domain; domain must not reference application
- Adapters cannot depend on other adapters
- Neither domain nor application packages may reference adapters

---

## Slices and cycle detection

```java
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

// Single-level slice (one package segment)
SlicesRuleDefinition.slices()
    .matching("..myapp.(*)..").should().beFreeOfCycles()

// Multi-level slice
SlicesRuleDefinition.slices()
    .matching("..myapp.(**)").should().beFreeOfCycles()

// No inter-slice dependencies
SlicesRuleDefinition.slices()
    .matching("..myapp.(**).service..").should().notDependOnEachOther()
```

Custom slice assignment (non-uniform package structures):

```java
SliceAssignment legacyPackageStructure = new SliceAssignment() {
    @Override
    public SliceIdentifier getIdentifierOf(JavaClass javaClass) {
        if (javaClass.getPackageName().startsWith("com.oldapp")) {
            return SliceIdentifier.of("Legacy");
        }
        if (javaClass.getName().contains(".esb.")) {
            return SliceIdentifier.of("ESB");
        }
        return SliceIdentifier.ignore();
    }
    @Override
    public String getDescription() { return "legacy package structure"; }
};

SlicesRuleDefinition.slices().assignedFrom(legacyPackageStructure)
    .should().beFreeOfCycles()
```

Cycle detection limits (via `archunit.properties`):

```properties
cycles.maxNumberToDetect=100              # default 100
cycles.maxNumberOfDependenciesPerEdge=20  # default 20
```

---

## Modules (ArchModule API)

```java
import com.tngtech.archunit.library.modules.ModuleRuleDefinition;

// Package-based modules
ModuleRuleDefinition.modules()
    .definedByPackages("..example.(*)..").should().beFreeOfCycles();

// Annotation-based modules
// Annotate package-info.java:
//   @AppModule(name="Module One", allowedDependencies={"Module Two"}, exposedPackages={"..module_one.api.."})
//   package com.myapp.example.module_one;

ModuleRuleDefinition.modules()
    .definedByAnnotation(AppModule.class)
    .should().respectTheirAllowedDependenciesDeclaredIn("allowedDependencies",
        consideringOnlyDependenciesInAnyPackage("..example.."))
    .andShould().onlyDependOnEachOtherThroughPackagesDeclaredIn("exposedPackages")
```

---

## PlantUML component diagrams

Derive rules directly from a `.puml` diagram file:

```java
import static com.tngtech.archunit.library.plantuml.rules.PlantUmlArchCondition.Configuration.*;
import static com.tngtech.archunit.library.plantuml.rules.PlantUmlArchCondition.adhereToPlantUmlDiagram;

URL myDiagram = getClass().getResource("my-diagram.puml");

classes().should(adhereToPlantUmlDiagram(myDiagram, consideringAllDependencies()));
classes().should(adhereToPlantUmlDiagram(myDiagram, consideringOnlyDependenciesInDiagram()));
classes().should(adhereToPlantUmlDiagram(myDiagram,
    consideringOnlyDependenciesInAnyPackage("..some.package..")));
classes().should(adhereToPlantUmlDiagram(myDiagram).ignoreDependencies(predicate));
```

Diagram format — components use bracket notation; stereotypes are package identifiers:

```
@startuml
[Some Source] <<..some.source..>>
[Some Target] <<..some.target..>> as target

[Some Source] --> target
@enduml
```

---

## General Coding Rules (predefined)

`com.tngtech.archunit.library.GeneralCodingRules` contains ready-made constants:

| Rule constant | What it checks |
|---|---|
| `NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS` | No `System.out`/`System.err` usage |
| `NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS` | No throwing `Exception`, `RuntimeException`, `Error`, `Throwable` |
| `NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING` | Forbid `java.util.logging`; use SLF4J/Log4j/Logback |
| `NO_CLASSES_SHOULD_USE_JODATIME` | Forbid JodaTime; use `java.time` |
| `NO_CLASSES_SHOULD_USE_FIELD_INJECTION` | Forbid field injection; require constructor injection |

Use them directly as `@ArchTest` fields:

```java
@ArchTest
static final ArchRule no_standard_streams = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
```

`com.tngtech.archunit.library.DependencyRules`:

| Rule constant | What it checks |
|---|---|
| `NO_CLASSES_SHOULD_DEPEND_UPPER_PACKAGES` | No dependency on classes in parent packages |

`com.tngtech.archunit.library.ProxyRules`:

| Method | What it checks |
|---|---|
| `no_classes_should_directly_call_other_methods_declared_in_the_same_class_that(predicate)` | Prevents direct self-calls that bypass proxies (e.g. `@Transactional`) |

---

## Custom predicates and conditions

### Custom predicate

```java
DescribedPredicate<JavaClass> haveAFieldAnnotatedWithPayload =
    new DescribedPredicate<JavaClass>("have a field annotated with @Payload") {
        @Override
        public boolean test(JavaClass input) {
            return input.getFields().stream()
                .anyMatch(f -> f.isAnnotatedWith(Payload.class));
        }
    };

// Compose with built-ins
DescribedPredicate<JavaClass> combined =
    haveAFieldAnnotatedWithPayload.and(resideInAPackage("..dto.."));
```

### Custom condition

```java
ArchCondition<JavaClass> onlyBeAccessedBySecuredMethods =
    new ArchCondition<JavaClass>("only be accessed by @Secured methods") {
        @Override
        public void check(JavaClass item, ConditionEvents events) {
            for (JavaMethodCall call : item.getMethodCallsToSelf()) {
                if (!call.getOrigin().isAnnotatedWith(Secured.class)) {
                    String message = String.format(
                        "Method %s is not @Secured", call.getOrigin().getFullName());
                    events.add(SimpleConditionEvent.violated(call, message));
                }
            }
        }
    };
```

### Predefined condition helpers (`ArchConditions`)

```java
ArchCondition<JavaClass> callEquals =
    ArchConditions.callMethod(Object.class, "equals", Object.class);
ArchCondition<JavaClass> callHashCode =
    ArchConditions.callMethod(Object.class, "hashCode");

ArchCondition<JavaClass> either = callEquals.or(callHashCode);
```

### Custom transformer (rules over packages / modules)

```java
ClassesTransformer<JavaPackage> packages =
    new AbstractClassesTransformer<JavaPackage>("packages") {
        @Override
        public Iterable<JavaPackage> doTransform(JavaClasses classes) {
            Set<JavaPackage> result = new HashSet<>();
            classes.getDefaultPackage().traversePackageTree(alwaysTrue(),
                pkg -> result.add(pkg));
            return result;
        }
    };

all(packages).that(containACoreClass()).should(notDependOnFrameworkInternals());
```

---

## Rule modifiers

```java
rule.because("ADR-004: services must not import persistence directly")
rule.as("custom rule description for failure output")
```

---

## Setup boilerplate

```java
@AnalyzeClasses(
    packages = "com.example.myapp",
    importOptions = {ImportOption.DoNotIncludeTests.class}
)
public class ArchitectureTests {
    // @ArchTest static final fields go here
}
```

**Always include `ImportOption.DoNotIncludeTests.class`.** Without it, ArchUnit scans test classes alongside production classes, causing false positives in naming-convention rules (e.g. `*ControllerTest` living in a `controller` package), field-injection rules (`@Autowired` in test fields), and `no_test_imports_in_production` (thousands of hits from legitimate test imports).

- JUnit 5: no runner annotation needed (ArchUnit JUnit 5 extension auto-registers)
- JUnit 4: add `@RunWith(ArchUnitRunner.class)`

---

## Standard patterns

### Forbidden package dependency

```java
// ADR-005: persistence layer must not be accessed directly from web layer
@ArchTest
static final ArchRule web_must_not_access_persistence =
    noClasses().that().resideInAPackage("..web..")
        .should().dependOnClassesThat().resideInAPackage("..persistence..")
        .because("ADR-005: web layer must go through the service layer");
```

### Forbidden dependency with exception

```java
// ADR-006: no direct Hibernate usage outside persistence package
@ArchTest
static final ArchRule no_hibernate_outside_persistence =
    noClasses().that()
        .resideOutsideOfPackage("..persistence..")
        .and().resideOutsideOfPackage("..config..")
        .should().dependOnClassesThat().resideInAPackage("org.hibernate..")
        .because("ADR-006: Hibernate is an implementation detail of persistence");
```

### Required annotation on matching methods

```java
// ADR-009: all public service methods must declare @Transactional
@ArchTest
static final ArchRule service_methods_must_be_transactional =
    methods().that().areDeclaredInClassesThat().resideInAPackage("..service..")
        .and().arePublic()
        .should().beAnnotatedWith(Transactional.class)
        .because("ADR-009: service layer owns transaction boundaries");
```

### Naming convention enforcement

```java
// ADR-003: repository implementations must end with 'Repository'
// IMPORTANT: use areTopLevelClasses() to exclude inner/anonymous classes ($1, $Foo)
// IMPORTANT: use allowEmptyShould(true) for patterns that may have no matching classes
@ArchTest
static final ArchRule repositories_named_correctly =
    classes().that().implement(Repository.class)
        .and().areTopLevelClasses()
        .should().haveSimpleNameEndingWith("Repository")
        .allowEmptyShould(true)
        .because("ADR-003: naming conventions aid discoverability");
```

### Layer naming conventions (controller / service / repository)

Use `..layerName` (no trailing `..`) **not** `..layerName..` when enforcing naming conventions.
`..controller..` matches ALL sub-packages (e.g. `controller.dto`), catching DTO classes.
`..controller` matches only packages whose last segment is `controller`.

```java
// BAD — catches classes in controller.dto, controller.request, etc.
classes().that().resideInAPackage("..controller..")
    .and().areNotInterfaces()
    .should().haveSimpleNameEndingWith("Controller");

// GOOD — only classes directly in a package named "controller"
classes().that().resideInAPackage("..controller")
    .and().areNotInterfaces()
    .and().areTopLevelClasses()
    .should().haveSimpleNameEndingWith("Controller");
```

The same applies to `..service` and `..repository` naming rules.

### Optional / empty-should rules

Rules for patterns that may not exist in every project (Util classes, custom Exceptions,
Helper fixtures, etc.) must call `.allowEmptyShould(true)` to avoid failing when no classes
match the `that()` clause.

```java
@ArchTest
static final ArchRule utility_classes_private_constructor =
    classes().that().haveSimpleNameEndingWith("Util")
        .or().haveSimpleNameEndingWith("Utils")
        .should().haveOnlyPrivateConstructors()
        .allowEmptyShould(true);

@ArchTest
static final ArchRule exception_naming_convention =
    classes().that().areAssignableTo(Exception.class)
        .should().haveSimpleNameEndingWith("Exception")
        .allowEmptyShould(true);
```

### Interface implementation requirement

```java
// ADR-011: all classes in ..event.. must implement DomainEvent
@ArchTest
static final ArchRule events_implement_domain_event =
    classes().that().resideInAPackage("..event..")
        .and().areNotInterfaces()
        .and().areTopLevelClasses()
        .should().implement(DomainEvent.class)
        .because("ADR-011: events must carry domain identity");
```

### No classes in the default (unnamed) package

```java
// `resideInAPackage("default package")` is INVALID — ArchUnit rejects the string.
// Correct approach: match FQNs that contain no dot (default-package classes have no dot).
@ArchTest
static final ArchRule no_classes_in_default_package =
    noClasses().should().haveNameMatching("[^.]+")
        .as("No class should reside in the default (unnamed) package");
```

### Cycle-free slices

```java
// ADR-002: no circular dependencies between top-level modules
@ArchTest
static final ArchRule no_cycles =
    SlicesRuleDefinition.slices()
        .matching("com.example.myapp.(*)..")
        .should().beFreeOfCycles()
        .because("ADR-002: cyclic module dependencies prevent independent deployment");
```

### Layered architecture (global — single hierarchy)

```java
// ADR-001: controller -> service -> persistence layering (single package hierarchy, whole app)
@ArchTest
static final ArchRule layering =
    layeredArchitecture().consideringAllDependencies()
        .layer("Controller").definedBy("..controller..")
        .layer("Service").definedBy("..service..")
        .layer("Persistence").definedBy("..persistence..")
        .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
        .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller")
        .whereLayer("Persistence").mayOnlyBeAccessedByLayers("Service")
        .because("ADR-001: strict layer isolation");
```

### Layered architecture (intra-module — multi-module apps)

```java
// ADR-001: no controller-to-repository shortcuts within the order module
// Cross-module callers of order.service are not flagged.
@ArchTest
static final ArchRule layer_access_rules_order =
    layeredArchitecture()
        .consideringOnlyDependenciesInAnyPackage("com.example.order..")
        .layer("Controller").definedBy("com.example.order.controller..")
        .layer("Service").definedBy("com.example.order.service..")
        .layer("Repository").definedBy("com.example.order.repository..")
        .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
        .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller")
        .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service")
        .allowEmptyShould(true)
        .because("ADR-001: no controller-to-repository shortcuts within the order module");
```

### Class containment check (name → package)

```java
// ADR-012: classes named *EventHandler must reside in the events package
@ArchTest
static final ArchRule event_handlers_in_events_package =
    classes().that().haveSimpleNameEndingWith("EventHandler")
        .and().areTopLevelClasses()
        .should().resideInAPackage("..events..")
        .allowEmptyShould(true)
        .because("ADR-012: event handlers belong in the events package");
```

### Inheritance naming convention

```java
// ADR-013: classes implementing Connection must be named *Connection
@ArchTest
static final ArchRule connection_implementations_named_correctly =
    classes().that().implement(Connection.class)
        .and().areTopLevelClasses()
        .should().haveSimpleNameEndingWith("Connection")
        .allowEmptyShould(true)
        .because("ADR-013: naming conventions aid discoverability of implementations");
```

### No Spring annotations in domain (with mapper exclusion)

```java
// Standard: domain objects must not carry Spring stereotype annotations.
// IMPORTANT: always exclude ..mapper.. sub-packages — MapStruct and other
// code-generation tools emit @Component on generated mapper implementations.
// Those classes are infrastructure glue, not domain objects.
@ArchTest
static final ArchRule no_spring_annotations_in_domain =
    noClasses().that()
        .resideInAnyPackage("..entity..", "..model..")
        .and().resideOutsideOfPackage("..mapper..")
        .and().areTopLevelClasses()
        .should().beAnnotatedWith(Component.class)
        .orShould().beAnnotatedWith(Service.class)
        .orShould().beAnnotatedWith(Repository.class)
        .orShould().beAnnotatedWith(Controller.class)
        .because("domain objects must not carry Spring stereotype annotations");
```

### Annotation-gated access

```java
// ADR-014: EntityManager may only be accessed from @Transactional callers
@ArchTest
static final ArchRule entity_manager_only_in_transactional =
    classes().that().areAssignableTo(EntityManager.class)
        .should().onlyHaveDependentClassesThat()
        .areAnnotatedWith(Transactional.class)
        .because("ADR-014: EntityManager must only be used within a transaction");
```

### No upper-package dependencies

```java
// Standard: no class should depend on a class in a parent package
@ArchTest
static final ArchRule no_upper_package_deps =
    DependencyRules.NO_CLASSES_SHOULD_DEPEND_UPPER_PACKAGES;
```

### Predefined general coding rule

```java
// ADR-008: no field injection
@ArchTest
static final ArchRule no_field_injection =
    GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
```

---

## Behavior notes

- **Output rule bodies only.** Do not wrap in a class declaration; do not add import statements — the generation script handles those.
- **Always include a source comment** on the line above the `@ArchTest` annotation linking to the originating ADR, e.g. `// ADR-007: ...`.
- Use `static final` fields, not methods.
- Prefer `.because(...)` on every rule with the ADR reference repeated in the string.

---

## What not to do

- Do not invent DSL methods. If a predicate or condition is not in this reference, check the ArchUnit Javadoc before using it.
- Do not emit class wrappers (`public class ArchitectureTests { ... }`).
- Do not add import statements.
- Do not use `ClassFileImporter` directly inside `@ArchTest` rules — `@AnalyzeClasses` handles class loading for the test runner.
- Do not use `..pkg..` (trailing `..`) in naming-convention rules; use `..pkg` (no trailing `..`) to match only the direct package, not sub-packages.
- Do not omit `.areTopLevelClasses()` in naming-convention or DI rules — anonymous/inner classes (`$1`, `$Inner`) live in the same package and will be caught, causing false positives.
- Do not omit `.allowEmptyShould(true)` for rules that target optional/project-specific patterns (Util, Exception subclasses, Helper fixtures). Without it the rule throws if zero classes match, even though that is not a violation.
- Do not write DI `no-new` rules that catch inner-class static factories calling their own constructor. Add `.and().areTopLevelClasses()` to the `that()` clause to limit scope to real service beans.
- Do not omit `importOptions = {ImportOption.DoNotIncludeTests.class}` from `@AnalyzeClasses`. Omitting it causes test classes to be scanned as production code, producing false positives in naming-convention rules, field-injection rules, and `no_test_imports_in_production`.
- Do not use `..model..` or `..entity..` alone in Spring-annotation-in-domain rules. Always add `.and().resideOutsideOfPackage("..mapper..")` — MapStruct-generated mapper implementations live under `..mapper..` sub-packages and carry `@Component` by design; flagging them is a false positive.
- Do not apply naming-convention rules without excluding annotation-processor-generated classes. These generators write `.java` files into the same source packages as hand-written code; ArchUnit scans them and produces false positives. Add the relevant `.and()` predicate to the `that()` clause for each affected layer:

  | Generator | Affected package | Generated name pattern | Exclusion predicate |
  |---|---|---|---|
  | MapStruct | `..mapper` | `*MapperImpl` | `.haveSimpleNameNotEndingWith("MapperImpl")` |
  | QueryDSL | entity packages | `Q*` (e.g. `QUserEntity`) | `.haveSimpleNameNotMatching("^Q[A-Z].*")` |
  | JPA static metamodel | entity packages | `*_` (trailing underscore) | `.haveSimpleNameNotEndingWith("_")` |
  | AutoValue | any | `AutoValue_*` | `.haveSimpleNameNotStartingWith("AutoValue_")` |
  | Immutables | any | `Immutable*` | `.haveSimpleNameNotStartingWith("Immutable")` |
  | Dagger 2 | component packages | `Dagger*`, `*_Factory`, `*_MembersInjector` | `.haveSimpleNameNotMatching("^Dagger.*|.*_Factory$|.*_MembersInjector$")` |

  Check the project's `pom.xml` or `build.gradle` for these dependencies before deciding which exclusions to apply. For a Spring Boot + JPA + MapStruct project, always apply the `MapperImpl`, `^Q[A-Z].*`, and `_` exclusions.
- Do not use `consideringAllDependencies()` for per-module layering rules in multi-module applications. It evaluates constraints against the entire scanned class set, so any class in any other module that legitimately calls into the defined layers will be flagged as a violation. Use `consideringOnlyDependenciesInAnyPackage("<module-root>..")` instead, scoped to the module's own root package.
