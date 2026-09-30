# standards-rules

Use this when planning or generating standard architecture enforcement rules for a project — structural rules that apply broadly regardless of project-specific decisions.

This guide lists architecture rules every project should enforce: layering, package structure, dependency direction, naming conventions, and component boundaries. Rules are described at intent level only; actual implementation is delegated to `archunit.md` (Java) or `depcruise.md` (TypeScript/JavaScript frontend).

> **Scope:** Architecture rules only. Security, code quality, logging, error handling, performance, and concurrency standards are out of scope for this guide.

> **CRITICAL — Never weaken a rule because existing code violates it.** When a rule from this catalogue fails against the current codebase, that is a **violation to surface**, not a reason to soften the rule. The correct response is:
> 1. Keep the rule exactly as the catalogue defines it.
> 2. Note the violation in the plan's Notes column (e.g. "currently violated by X — flagged for refactoring").
> 3. Let the test fail as a gate until the code is fixed.
>
> Weakening a rule (e.g., changing "must be in `..config..`" to "must not be in `..api..`") defeats the purpose of architecture enforcement — it makes the test pass by lowering the bar to match broken code, not by fixing the code. This applies equally to `/arch-tests-plan` and `/arch-tests-gen`.

---

## Backend

Rules for server-side code: Java (ArchUnit), Node.js/TypeScript services.


### Universal (backend)

Apply to all backend projects regardless of architecture style, framework, or language.

#### Cycles

##### `no_cyclic_dependencies`
- **Intent:** Assert no cyclic package or class-level dependencies exist within the application.
- **Tool:** archunit
- **Languages:** Java
- **Severity:** high

##### `no_intra_layer_cycles`
- **Intent:** Assert no cycles exist between classes within the same layer (e.g., between classes inside the `service` package). Uses `SlicesRuleDefinition.slices().matching("..service.(*)..").should().beFreeOfCycles()` — repeat per layer.
- **Tool:** archunit
- **Languages:** Java
- **Severity:** high
- **Safe-defaults:** Call `.allowEmptyShould(true)` for layers that may have no sub-packages to slice.
- **Multi-module note:** The slice pattern `..service.(.*)..` matches sub-packages across all modules simultaneously. In multi-module projects, generate one row per module using `consideringOnlyDependenciesInAnyPackage("<module-root>..")` to avoid false cycle reports between sub-packages in different modules; see the multi-module callout in *By architectural style*.

##### `no_inter_aggregate_cycles`
- **Intent:** Assert no cycles exist between aggregate or module root packages (e.g., `order` must not cycle with `inventory`). Uses `SlicesRuleDefinition.slices().matching("..domain.(*)..").should().beFreeOfCycles()`.
- **Tool:** archunit
- **Languages:** Java
- **Severity:** low
- **Optional:** yes — include only for DDD or module-structured projects where aggregates are represented as distinct sub-packages under a common parent (e.g., `domain.order`, `domain.inventory`).

---

#### Naming

##### `no_classes_in_default_package`
- **Intent:** Assert that no class lives in the unnamed (default) package. All classes must be in a named package.
- **Tool:** archunit
- **Languages:** Java
- **Severity:** high

##### `naming_conventions`
- **Intent:** Assert that classes in each layer follow their naming convention (e.g., `*Controller`, `*Service`, `*Repository`).
- **Tool:** archunit
- **Languages:** Java
- **Severity:** high
- **Safe-defaults:** Use `..layerName` (no trailing `..`) in the `that()` clause — `..controller..` also matches sub-packages like `controller.dto`, catching DTO classes. Always add `.and().areTopLevelClasses()` to exclude anonymous/inner classes (`$1`, `$Inner`). Add `.allowEmptyShould(true)` for layers that may have no non-interface classes (e.g. repositories that are all Spring Data interfaces).
- **arch-tests-gen warning:** Code generators naturally write `..controller..` (trailing `..`) as the obvious pattern. This is wrong — it flags DTO and other sub-package classes for not being named `*Controller`. Always review generated sketches and confirm the `that()` clause uses the non-trailing form (e.g. `resideInAPackage("..controller")`) combined with `.areTopLevelClasses()`. This error surfaces at generation time, not planning time.
- **Code-generator false positives:** Annotation processors generate `.java` files that land in the same packages as hand-written classes and will be scanned by ArchUnit. Add the relevant exclusion to the `that()` clause for each affected layer:

  | Generator | Affected package | Generated name pattern | Exclusion predicate |
  |---|---|---|---|
  | MapStruct | `..mapper` | `*MapperImpl` | `.haveSimpleNameNotEndingWith("MapperImpl")` |
  | QueryDSL | entity packages | `Q*` (e.g. `QUserEntity`) | `.haveSimpleNameNotMatching("^Q[A-Z].*")` |
  | JPA static metamodel | entity packages | `*_` (trailing underscore) | `.haveSimpleNameNotEndingWith("_")` |
  | AutoValue | any | `AutoValue_*` | `.haveSimpleNameNotStartingWith("AutoValue_")` |
  | Immutables | any | `Immutable*` | `.haveSimpleNameNotStartingWith("Immutable")` |
  | Dagger 2 | component packages | `Dagger*`, `*_Factory`, `*_MembersInjector` | `.haveSimpleNameNotMatching("^Dagger.*|.*_Factory$|.*_MembersInjector$")` |

  Apply only the exclusions relevant to the project's actual dependencies. For most Spring Boot + JPA + MapStruct projects the three to add are `MapperImpl`, `^Q[A-Z].*`, and `_`.

##### `class_containment_checks`
- **Intent:** Assert that classes with a specific name prefix or suffix reside in the appropriate package (e.g., classes named `*Service` must be in `..service..`). Enforces the inverse of naming conventions: that the name implies the package location.
- **Tool:** archunit
- **Languages:** Java
- **Severity:** high
- **Note:** Generate one rule per name pattern / target package pair. Use `.allowEmptyShould(true)` for patterns that may not yet exist in the codebase.

##### `inheritance_naming`
- **Intent:** Assert that classes implementing a given interface follow a naming convention derived from that interface (e.g., classes implementing `Connection` must have a name ending in `Connection`). Enforces discoverability of implementations.
- **Tool:** archunit
- **Languages:** Java
- **Severity:** low
- **Optional:** yes — include only when the project has a consistent pattern of interface-named implementations. Use `.allowEmptyShould(true)`.

##### `no_generic_exception_throws`
- **Intent:** Assert that no method declares `throws Exception` or `throws Throwable`. All thrown exceptions must be specific types, making error handling predictable and self-documenting.
- **Tool:** archunit
- **Languages:** Java
- **Severity:** high
- **Note:** `noClasses().should().callMethodWhere(target().name().matches("throw.*")).and().declareThrowing(Exception.class)` — alternatively, use a custom `ArchCondition` that inspects declared exceptions on methods. Call `.allowEmptyShould(true)`.

##### `exception_naming_convention`
- **Intent:** Assert that any class extending `Exception` or `RuntimeException` has a name ending in `Exception`, and that any class named `*Exception` extends `Throwable`.
- **Tool:** archunit
- **Languages:** Java
- **Severity:** low
- **Safe-defaults:** Must call `.allowEmptyShould(true)` — projects with no custom exceptions will have zero matching classes, triggering a false failure.

---

#### Anti-patterns

##### `utility_classes_private_constructor`
- **Intent:** Assert that classes whose methods are all static (utility classes) have a private constructor and are final, preventing instantiation.
- **Tool:** archunit
- **Languages:** Java
- **Severity:** low
- **Safe-defaults:** Must call `.allowEmptyShould(true)` — many projects have no Util/Utils classes at the time of first run, causing the rule to throw with "failed to check any classes" rather than pass.

##### `no_test_imports_in_production`
- **Intent:** Assert that production source classes do not import test framework packages (e.g., `org.junit`, `org.mockito`, `org.testng`).
- **Tool:** archunit
- **Languages:** Java
- **Severity:** high

##### `no_system_out`
- **Intent:** Assert that no production class calls `System.out` or `System.err`. All output must go through a logging framework. Uses `GeneralCodingRules.NO_CLASSES_SHOULD_USE_STANDARD_STREAMS`.
- **Tool:** archunit
- **Languages:** Java
- **Severity:** high

##### `no_business_logic_in_controllers`
- **Intent:** Assert that controller classes do not directly depend on repository or persistence classes — all data access must flow through a service layer. Implemented as `noClasses().that().resideInAPackage("..controller..").should().dependOnClassesThat().resideInAnyPackage("..repository..", "..persistence..", "..dao..")`.
- **Tool:** archunit
- **Languages:** Java
- **Severity:** high
- **Note:** Complements `layer_access_rules` with a stricter anti-pattern check focused on the controller→repository shortcut.
- **Multi-module note:** Patterns `..controller..` and `..repository..` match across all modules simultaneously. In multi-module projects, generate one row per module using `consideringOnlyDependenciesInAnyPackage("<module-root>..")` to avoid flagging cross-module patterns as violations; see the multi-module callout in *By architectural style*.

##### `no_static_utility_in_domain`
- **Intent:** Assert that no class in domain packages is a static-only utility class (all methods static, no instance state). Domain packages must contain only domain objects, value objects, and services — not general-purpose helpers.
- **Tool:** archunit
- **Languages:** Java
- **Severity:** low
- **Optional:** yes — requires a custom `ArchCondition` to check that no method is non-static. Include only when the project enforces strict domain purity (Onion/Clean/Hexagonal styles).

##### `no_dependency_on_deprecated`
- **Intent:** Assert that no production class depends on a class or interface marked as deprecated. Forces callers to migrate away from deprecated types before removal.
- **Tool:** archunit (Java); semgrep (TypeScript)
- **Languages:** Java, TypeScript
- **Severity:** low
- **Note (Java):** Use `noClasses().should().dependOnClassesThat().areAnnotatedWith(Deprecated.class).allowEmptyShould(true)`.
- **Note (TypeScript):** No runtime annotation exists — deprecation is conveyed via JSDoc `/** @deprecated */`. Use a Semgrep rule that matches imports of symbols whose JSDoc contains `@deprecated`. Reliability depends on team JSDoc discipline.
- **Safe-defaults:** Call `.allowEmptyShould(true)`.


##### `no_business_logic_in_route_handlers`
- **Intent:** Assert that route/controller handler functions contain only orchestration — calling services and returning responses. Conditional business decisions, calculations, and domain transformations that belong in a service layer must not appear inline in handlers.
- **Tool:** llm
- **Languages:** TypeScript, JavaScript
- **Severity:** low
- **Scope:** `src/**/routes/**/*.ts`, `src/**/controllers/**/*.ts`, `src/**/handlers/**/*.ts`

##### `no_validation_in_repositories`
- **Intent:** Assert that repository and data-access functions contain no input validation logic. Validation belongs in the service or use-case layer; repositories are responsible only for persistence operations.
- **Tool:** llm
- **Languages:** TypeScript, JavaScript, Java
- **Severity:** low
- **Scope:** `src/**/repositories/**/*.ts`, `src/**/repos/**/*.ts`, `src/main/java/**/repository/**/*.java`

---

### By architectural style (backend)

Pick one primary style. Modular monolith overlays any of the first three. Include the rules for the chosen style in addition to all Universal rules.

> **Multi-module projects:** Rules that use layer-based package patterns (e.g. `..controller..`, `..service..`, `..repository..`) match classes across **all modules** when applied without scoping. In multi-module projects, generate **one plan row per module** for each affected rule (e.g. `rule_name_order`, `rule_name_inventory`), using `consideringOnlyDependenciesInAnyPackage("<module-root>..")`. Do **not** use a single global row — that checks the entire scanned class set and will flag legitimate cross-module calls as violations.
>
> **Affected rules:** `layer_access_rules`, `no_business_logic_in_controllers`, `no_intra_layer_cycles`. Additionally: `no_upper_package_deps` produces false alarms if sub-packages exist within a layer (e.g. `service.impl`, `model.mapper`) — only include when the project enforces flat, single-level packages. `naming_conventions` produces false alarms if the `that()` clause uses trailing `..` (e.g. `..controller..` also matches `controller.dto`) — always add `.areTopLevelClasses()`.

#### Layered

##### `layer_access_rules`
- **Intent:** Enforce that controllers only access services, services only access repositories, and repositories do not access controllers or services.
- **Tool:** archunit
- **Languages:** Java
- **Severity:** high
- **Multi-module note:** See the multi-module callout above — generate one row per module using `consideringOnlyDependenciesInAnyPackage("<module-root>..")`.

##### `no_upper_package_deps`
- **Intent:** Assert that no class depends on a class in a parent package (i.e., no upward package references). Uses `DependencyRules.NO_CLASSES_SHOULD_DEPEND_UPPER_PACKAGES`.
- **Tool:** archunit
- **Languages:** Java
- **Severity:** low
- **Optional:** yes — do not include if the project uses sub-packages within a layer (e.g. `model.mapper` depending on `model`, `service.impl` depending on `service`). Only include when the project enforces a flat, single-level package per layer.
- **arch-tests-plan warning:** The planner cannot infer sub-package usage from `arch-rules.md` alone. If `arch-tests-plan` includes this rule, manually verify that no layer uses sub-packages (e.g. `service.impl`, `model.mapper`, `controller.dto`) before enabling it in CI. False alarms are silent until first run.

##### `service_depends_on_repository_interfaces`
- **Intent:** Assert that service classes depend on repository interface types, not on concrete implementations.
- **Tool:** archunit
- **Languages:** Java
- **Severity:** low
- **Optional:** yes — include only when the project has a dedicated `impl` sub-package. Skip if all repositories are Spring Data interfaces.
- **Note:** `noClasses().that().resideInAPackage("..service..").should().dependOnClassesThat().haveSimpleNameEndingWith("Impl").and().resideInAPackage("..repository..")`.

---

#### Onion / Clean / Hexagonal

##### `no_domain_infrastructure_imports`
- **Intent:** Assert that domain/core packages do not import from infrastructure or framework packages.
- **Tool:** archunit
- **Languages:** Java
- **Severity:** high

##### `interfaces_in_api_package`
- **Intent:** Assert that public-facing ports/interfaces reside in an `api` or `port` package, not in `impl`.
- **Tool:** archunit
- **Languages:** Java
- **Severity:** low

##### `onion_architecture`
- **Intent:** Enforce onion / hexagonal architecture constraints: domain models form the inner core; application services may use domain; adapters cannot depend on each other; neither domain nor application may reference adapters.
- **Tool:** archunit
- **Languages:** Java
- **Severity:** low
- **Optional:** yes — include **only** for projects whose root packages are `domain`, `application`, `adapter`, `port`, `infrastructure`, or `usecase`. Mutually exclusive with `layer_access_rules`.

---

#### Modular monolith

##### `no_forbidden_package_deps`
- **Intent:** Assert that specific packages do not depend on other specific packages (e.g., `domain` must not depend on `infrastructure`, `api` must not depend on `db`).
- **Tool:** archunit
- **Languages:** Java
- **Severity:** high
- **Note:** Generate one rule per forbidden dependency pair (source package → target package). Each rule should name the specific packages and the architectural reason the dependency is forbidden. Can overlay any primary style (Layered, Onion, Hexagonal) to enforce module boundaries.
- **JPA cross-module entities:** When forbidding access to another module's `..entity..` package, always exclude the source module's own entity package from the `that()` clause using `.and().resideOutsideOfPackage("<source-module>.entity..")`. JPA `@ManyToOne` / `@OneToMany` relationships between entities in different modules are a legitimate pattern for foreign-key integrity — banning them forces unnecessary indirection. Only controller/service → other-module entity access should be forbidden.

##### `no_shared_depends_on_feature`

> **Shared vs. business modules.** A *shared module* (also called *core* or *platform* module) provides cross-cutting infrastructure that any business module may depend on. A *business module* (also called *feature* or *domain* module) implements a specific business capability. The dependency rule is: **business → shared is allowed; shared → business is forbidden.**
>
> **IMPORTANT — Verify classification against project docs and intended design.** The examples below are typical AppFW patterns. When the catalogue lists a module as shared and the code currently violates that boundary (e.g., a shared module imports from a business module), this is a **code violation to flag**, not a reason to reclassify the module. The test should fail and surface the violation for refactoring.
>
> Only reclassify a module away from the catalogue's examples if project documentation (ADR, CONTEXT.md) explicitly states a different intent.
>
> Examples of shared modules (drawn from AppFW standards):
> | Module | Purpose |
> |---|---|
> | `reporting` | Cross-cutting report generation and export |
> | `user-auth` | User authentication and session management |
> | `fileupload` | Unified file upload, storage, and virus-scan lifecycle |
> | `mcc-auth` / `mcns` / `mpds` | MCC authentication, MCNS, and MPDS integration |
> | `observability` | Structured logging, tracing, and context propagation |
> | `interface` | Inbound/outbound batch file exchange, processing pipelines, and acknowledgment handling |
> | `mfa` | Multi-factor authentication |
>
> Any module whose sole consumers are other modules (never called directly by end-user features) is a strong candidate for shared status.

- **Intent:** Assert that shared/core/platform modules do not depend on any business/feature module. The dependency must always flow business → shared, never the reverse. A shared module that imports from a business module creates a hidden coupling that defeats the purpose of modular decomposition.
- **Tool:** archunit
- **Languages:** Java
- **Severity:** high
- **Note:** `noClasses().that().resideInAnyPackage("..modules.reporting..", "..modules.fileupload..", "..modules.observability..", "..modules.mfa..", "..modules.interface..", "..modules.auth..").should().dependOnClassesThat().resideInAnyPackage("<list of business module packages>")`. Adjust the shared and business package lists to match the project's actual module layout.
- **Multi-module note:** In multi-module Maven/Gradle projects where shared and business modules are separate build artifacts, this rule can also be enforced at the build level by ensuring shared module POMs never declare a dependency on a business module artifact. The ArchUnit rule acts as a second safety net for transitive or reflection-based coupling.

---

### By framework (backend)

Include rules for each framework present in the project.

#### Spring / Spring Boot

##### `configuration_classes_in_config_package`
- **Intent:** Assert that classes annotated with `@Configuration` reside in a `..config..` package.
- **Tool:** archunit
- **Languages:** Java
- **Severity:** high

##### `no_field_injection`
- **Intent:** Assert that no class uses `@Autowired` or `@Inject` field injection. All Spring bean dependencies must be injected via constructor.
- **Tool:** archunit
- **Languages:** Java
- **Severity:** high
- **Warning:** Do NOT use `GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION` — it also catches `@Value` fields. Instead: `noFields().that().areDeclaredInClassesThat().areTopLevelClasses().should().beAnnotatedWith(Autowired.class).allowEmptyShould(true)`. Add a separate rule for `@Inject` if the project uses JSR-330.

##### `no-new-inside-service`
- **Intent:** Flag `new` instantiation of service or repository classes inside other service classes (should be injected).
- **Tool:** archunit
- **Languages:** Java
- **Severity:** low
- **Safe-defaults:** Add `.and().areTopLevelClasses()` to the `that()` clause to exclude static inner classes.

##### `no_transactional_outside_service`
- **Intent:** Assert that `@Transactional` does not appear on controller or domain classes. Transaction management belongs at the use-case boundary.
- **Tool:** archunit
- **Languages:** Java
- **Severity:** high
- **Note:** Write two rules — one banning `@Transactional` in `..controller` and one in `..domain`. Both call `.allowEmptyShould(true)`.

##### `controllers_must_be_annotated`
- **Intent:** Assert that classes in `..controller..` packages are annotated with `@RestController` or `@Controller`. Catches controller classes that forgot the annotation and won't be picked up by Spring's component scan.
- **Tool:** archunit
- **Languages:** Java
- **Severity:** high
- **Note:** `classes().that().resideInAPackage("..controller").and().areTopLevelClasses().and().areNotInterfaces().should().beAnnotatedWith(RestController.class).orShould().beAnnotatedWith(Controller.class).allowEmptyShould(true)`. Use `..controller` (no trailing `..`) to avoid matching sub-packages like `controller.dto`.

---

#### JPA / Hibernate

##### `annotation_gated_access`
- **Intent:** Assert that classes of a specific type (e.g., `EntityManager`) may only be accessed by callers bearing a required annotation (e.g., `@Transactional`).
- **Tool:** archunit
- **Languages:** Java
- **Severity:** low
- **Note:** Use `classes().that().areAssignableTo(X.class).should().onlyHaveDependentClassesThat().areAnnotatedWith(Ann.class)`.

##### `entities_have_required_annotations`
- **Intent:** Assert that every class annotated with `@Entity` is also annotated with `@Table`. Ensures explicit table mapping.
- **Tool:** archunit
- **Languages:** Java
- **Severity:** low
- **Note:** `classes().that().areAnnotatedWith(Entity.class).should().beAnnotatedWith(Table.class).allowEmptyShould(true)`.

##### `entities_only_in_entity_packages`
- **Intent:** Assert that classes annotated with `@Entity` reside in `..entity..` packages. Prevents entity classes from leaking into controller, service, or other packages.
- **Tool:** archunit
- **Languages:** Java
- **Severity:** high
- **Note:** `classes().that().areAnnotatedWith(Entity.class).should().resideInAPackage("..entity..").allowEmptyShould(true)`.

##### `no_entity_in_controllers`
- **Intent:** Assert that controller classes do not depend on `@Entity`-annotated classes. Forces DTO usage at the API boundary, preventing lazy-loading exceptions and accidental data exposure (especially relevant for HIPAA / sensitive-data contexts).
- **Tool:** archunit
- **Languages:** Java
- **Severity:** high
- **Note:** `noClasses().that().resideInAPackage("..controller..").should().dependOnClassesThat().areAnnotatedWith(Entity.class).allowEmptyShould(true)`. In multi-module projects, scope with `consideringOnlyDependenciesInAnyPackage("<module-root>..")`.
- **Multi-module note:** Pattern `..controller..` matches across all modules — generate one row per module in multi-module projects.

##### `repositories_must_be_interfaces`
- **Intent:** Assert that classes in `..repository..` packages are interfaces, not concrete classes. Spring Data repositories must be interfaces extending `JpaRepository` / `CrudRepository`; concrete implementations bypass the Spring Data proxy mechanism.
- **Tool:** archunit
- **Languages:** Java
- **Severity:** high
- **Note:** `classes().that().resideInAPackage("..repository").and().areTopLevelClasses().should().beInterfaces().allowEmptyShould(true)`. Use `..repository` (no trailing `..`) to avoid matching sub-packages. Exclude custom repository implementation classes if the project uses the `*RepositoryImpl` Spring Data custom fragment pattern — add `.and().haveSimpleNameNotEndingWith("Impl")`.

##### `mappers_in_mapper_packages`
- **Intent:** Assert that classes annotated with MapStruct's `@Mapper` reside in `..mapper..` packages. Prevents mapper classes from being scattered across service, controller, or other packages.
- **Tool:** archunit
- **Languages:** Java
- **Severity:** high
- **Optional:** yes — include only when the project uses MapStruct.
- **Note:** `classes().that().areAnnotatedWith("org.mapstruct.Mapper").should().resideInAPackage("..mapper..").allowEmptyShould(true)`.

---

#### JUnit / test frameworks

##### `no-logic-in-test-helpers`
- **Intent:** Assert that shared test helper/fixture classes contain no assertion logic (only builders and factories).
- **Tool:** archunit
- **Languages:** Java
- **Severity:** low
- **Safe-defaults:** Must call `.allowEmptyShould(true)`.

##### `test-classes-in-test-source`
- **Intent:** Assert that classes annotated with `@Test` or extending test base classes live under the test source root, not main.
- **Tool:** archunit
- **Languages:** Java
- **Severity:** low

---

## Frontend

Rules for client-side code: TypeScript/JavaScript projects using React, Vue, Angular, or similar frameworks. All rules in this section use `Tool: depcruise` unless stated otherwise, and are implemented via the `depcruise.md` reference.

---

### Universal (frontend)

#### `no_circular_module_deps`
- **Intent:** Assert that no circular dependency exists anywhere in the module graph. Circular imports cause unpredictable module load order, hidden coupling, and bundler failures.
- **Tool:** depcruise
- **Languages:** TypeScript, JavaScript
- **Severity:** high
- **depcruise:** Enable `circular: true` on the forbidden rule. Depcruise's built-in cycle detection traverses the full module graph — no `from`/`to` path patterns are needed.

---

### Module / feature boundaries

#### `no_feature_cross_imports`
- **Intent:** Assert that a feature module may not import directly from a sibling feature module. All cross-feature communication must flow through `shared/`, a public API barrel (`index.ts`), or an event/message bus.
- **Tool:** depcruise
- **Languages:** TypeScript, JavaScript
- **Severity:** low
- **depcruise:** `from.path`: `^src/features/([^/]+)/`. `to.path`: `^src/features/` with `to.pathNot`: `^src/features/$1/`. Depcruise v11+ supports captured group references (`$1`) in `to.pathNot`.

#### `shared_no_feature_imports`
- **Intent:** Assert that shared/common/utils code does not depend on any feature module. The dependency must always flow feature → shared, never the reverse.
- **Tool:** depcruise
- **Languages:** TypeScript, JavaScript
- **Severity:** low
- **depcruise:** `from.path`: `^src/(shared|common|utils|lib)/`. `to.path`: `^src/features/`.

#### `no_cross_page_imports`
- **Intent:** Assert that page-level components do not import from other page-level components. Pages are composition roots — they should import from features and shared, never from sibling pages.
- **Tool:** depcruise
- **Languages:** TypeScript, JavaScript
- **Severity:** low
- **depcruise:** `from.path`: `^src/(pages|views|app/.*page)/([^/]+)/`. `to.path`: `^src/(pages|views|app/.*page)/` with `to.pathNot`: same page group (`$2`).

#### `barrel_only_public_api`
- **Intent:** Assert that code outside a feature directory imports only through the feature's public barrel (`index.ts`), never from internal paths. Enforces an explicit public API boundary per feature.
- **Tool:** depcruise
- **Languages:** TypeScript, JavaScript
- **Severity:** low
- **Optional:** yes — include only when feature modules have explicit `index.ts` barrels.
- **depcruise:** `from.pathNot`: `^src/features/([^/]+)/`. `to.path`: `^src/features/([^/]+)/` with `to.pathNot`: `^src/features/[^/]+/index\\.(ts|tsx)$`.

#### `constants_no_feature_imports`
- **Intent:** Assert that constants, configuration, and environment files do not import from feature modules.
- **Tool:** depcruise
- **Languages:** TypeScript, JavaScript
- **Severity:** low
- **depcruise:** `from.path`: `^src/(constants|config|env)/`. `to.path`: `^src/features/`.

---

### Layer boundaries

#### `no_direct_http_in_components`
- **Intent:** Assert that UI component files do not import HTTP client libraries or project-level API modules directly. All HTTP calls must be encapsulated in a service or custom hook layer.
- **Tool:** depcruise
- **Languages:** TypeScript, JavaScript
- **Severity:** low
- **depcruise:** Two sub-rules: (1) `from.path`: `^src/(components|pages|views|features)`, `to.dependencyTypes`: `["npm"]`, `to.path`: `^(axios|node-fetch|got|ky|cross-fetch|superagent)$`; (2) same `from.path`, `to.path`: `^src/api/`.
- **Note:** Data-fetching hook libraries (`react-query`, `swr`) are fine in components — exclude them from the `to.path` pattern.

#### `no_store_in_non_hook_files`
- **Intent:** Assert that only custom hook files may import from the application store (Redux, Zustand, Pinia, etc.). Component files must use selector hooks only.
- **Tool:** depcruise
- **Languages:** TypeScript, JavaScript
- **Severity:** low
- **Optional:** yes — include only for projects using a centralised client-side store. Skip for projects relying solely on server state (react-query, SWR).
- **depcruise:** `from.path`: `^src/(components|pages|views|features)`, `from.pathNot`: `src/(hooks|composables)/`. `to.path`: `^src/(store|redux|state)/`.

#### `styles_not_in_logic_files`
- **Intent:** Assert that service, hook, utility, and store files do not import CSS or SCSS files. Style imports belong only in component files.
- **Tool:** depcruise
- **Languages:** TypeScript, JavaScript
- **Severity:** low
- **depcruise:** `from.path`: `^src/(services|hooks|composables|utils|store|api)/`. `to.path`: `\\.(css|scss|sass|less|module\\.css|module\\.scss)$`. Severity: warn.

---

### By framework (frontend)

#### React / Next.js

##### `no_server_imports_in_client_components`
- **Intent:** Assert that files marked `"use client"` do not import Node.js-only or server-only modules (`fs`, `path`, `next/headers`, etc.). Server modules in client bundles cause build failures.
- **Tool:** depcruise
- **Languages:** TypeScript, JavaScript
- **Severity:** low
- **Optional:** yes — Next.js App Router projects only.
- **depcruise:** `to.dependencyTypes`: `["npm"]`, `to.path`: `^(fs|path|crypto|os|net|tls|child_process|next/headers|next/cookies|server-only)$`. Severity: error.

##### `hooks_directory_only`
- **Intent:** Assert that files exporting custom React hooks (`use*` named exports) reside in a `hooks/` directory.
- **Tool:** llm
- **Languages:** TypeScript, JavaScript
- **Severity:** low
- **Optional:** yes — include only when the project enforces a dedicated hooks directory by convention or ADR.
- **Scope:** `src/**/*.ts`, `src/**/*.tsx`
- **Pre-filter:** Grep for `^export (function|const) use[A-Z]` first; pass only matching files. Max 20 files per run.

---

## LLM-reviewed

Rules that require semantic judgment and cannot be expressed as structural or pattern-matching rules. All rules in this section use `Tool: llm`. These apply to any TypeScript/JavaScript project, backend or frontend.

**All LLM-reviewed rules have an implicit severity of `low`** — they are always placed in the For Consideration section. They consume tokens on every run and must be opted into deliberately.

Each rule includes a `**Scope:**` field. Keep scopes narrow to stay low-token.

---

### Naming

#### `meaningful_public_api_names`
- **Intent:** Assert that exported function names clearly describe the domain action they perform. Generic verbs without a domain noun (`process`, `handle`, `run`, `execute`) fail this rule.
- **Tool:** llm
- **Languages:** TypeScript, JavaScript
- **Scope:** `src/**/*.ts`
- **Pre-filter:** Grep for `^export (function|class)` first; pass only matching files. Max 20 files per run.

---

### Cohesion

#### `single_module_responsibility`
- **Intent:** Assert that each module or file addresses one domain concept or one layer concern. Files that mix unrelated responsibilities fail this rule.
- **Tool:** llm
- **Languages:** TypeScript, JavaScript
- **Scope:** `src/**/*.ts`
- **Pre-filter:** Sample at most one file per directory; max 15 files per run. Prioritise files over 100 lines.
- **Optional:** yes — include when cohesion discipline is required by ADR or team convention.

---

### Documentation fidelity

#### `jsdoc_param_accuracy`
- **Intent:** Assert that JSDoc `@param` descriptions accurately describe the role and meaning of the parameter, not just restate the name.
- **Tool:** llm
- **Languages:** TypeScript, JavaScript
- **Scope:** `src/**/*.ts`
- **Pre-filter:** Grep for `@param` first; pass only matching files. Max 20 files per run.

#### `module_comment_accuracy`
- **Intent:** Assert that the module-level or file-level comment accurately describes what the module does.
- **Tool:** llm
- **Languages:** TypeScript, JavaScript
- **Scope:** `src/**/*.ts`
- **Pre-filter:** Grep for `^\s*/\*\*` first; pass only matching files. Max 20 files per run.
- **Optional:** yes — include only when module-level documentation is a team requirement.

---

## By concern

| Concern | Rules to apply |
|---|---|
| Persistence boundary | `annotation_gated_access` (JPA); `no_domain_infrastructure_imports` (Onion/Clean); `entities_have_required_annotations` (JPA); `entities_only_in_entity_packages` (JPA); `no_entity_in_controllers` (JPA); `repositories_must_be_interfaces` (JPA) |
| API / port boundary | `interfaces_in_api_package` (Onion/Hexagonal); `no_forbidden_package_deps` per module; `no_entity_in_controllers` (JPA) |
| Dependency injection | `no_field_injection`, `no-new-inside-service` (Spring) |
| Domain purity | `no_spring_annotations_in_domain`, `no_domain_infrastructure_imports`, `no_static_utility_in_domain` |
| Test isolation | `no_test_imports_in_production`, `no-logic-in-test-helpers`, `test-classes-in-test-source` |
| Deprecated / legacy migration | `no_dependency_on_deprecated` |
| Transaction discipline | `no_transactional_outside_service` (Spring); `annotation_gated_access` (JPA) |
| Interface/implementation boundary | `service_depends_on_repository_interfaces` (Layered); `repositories_must_be_interfaces` (JPA) |
| Spring annotation correctness | `controllers_must_be_annotated`, `configuration_classes_in_config_package` (Spring) |
| Class containment | `entities_only_in_entity_packages` (JPA); `mappers_in_mapper_packages` (MapStruct); `configuration_classes_in_config_package` (Spring); `class_containment_checks` (Universal) |
| Exception handling | `no_generic_exception_throws`, `exception_naming_convention` (Universal) |
| Module/shared boundary (backend) | `no_shared_depends_on_feature` (Modular monolith) |
| Module/shared boundary (frontend) | `shared_no_feature_imports`, `constants_no_feature_imports` (depcruise) |
| Feature isolation (frontend) | `no_feature_cross_imports`, `barrel_only_public_api`, `no_cross_page_imports` (depcruise) |
| Layer boundary (frontend) | `no_direct_http_in_components`, `no_store_in_non_hook_files`, `styles_not_in_logic_files` (depcruise) |
| Circular dependencies | `no_cyclic_dependencies`, `no_intra_layer_cycles` (backend); `no_circular_module_deps` (frontend) |
| Layer semantics (TypeScript) | `no_business_logic_in_route_handlers`, `no_validation_in_repositories` (LLM) |
| Naming semantics | `meaningful_public_api_names` (LLM) |
| Cohesion | `single_module_responsibility` (LLM) |
| Documentation fidelity | `jsdoc_param_accuracy`, `module_comment_accuracy` (LLM) |

---

## How to use

When generating rules for a new project:

1. **Identify stack position** — backend (Java/Node.js), frontend (TypeScript/JS), or both. Select the appropriate section(s).
2. **Identify languages and frameworks** in use. Skip any rule whose "Languages" field does not include a language present in the project.
3. **Include all applicable rules** from Universal and the chosen style/framework groups. Omit only rules that are provably irrelevant. For example, a layered Spring Boot project should include all Universal rules, all Layered rules, and all Spring rules. A React frontend should include all Universal frontend rules and the React-specific rules.
4. **Severity drives placement:** `high` → Implemented Rules (enforced in CI); `low` → For Consideration. LLM rules always land in For Consideration regardless of severity.

