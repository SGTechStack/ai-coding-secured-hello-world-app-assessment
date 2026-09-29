# Test Plan

Generated: 2026-09-29
Stack: Java (Spring Boot) + TypeScript/JavaScript (React) / Spring Boot, Spring Data JPA (H2) / Maven or Gradle (TBD — no build file yet)
Architecture style: flat-layered (declared by user; no source code present yet to confirm)
Dependency direction: down (declared) — controller → service → repository; nothing flows upward
Frontend: React (intended, per PRD) — structure not yet created; classify with `/arch-tests-gen-feature` once `src/` exists

> **Note — greenfield project.** This repository currently contains no application source code, build files, or ADRs. The plan below is derived from the intended stack in `prd/assessment-prd.md` (React + Spring Boot + Spring Data JPA) and the user-declared flat-layered style. Rules use the catalogue's standard package patterns (`..controller`, `..service`, `..repository`, `..entity`, `..config`). Adjust package names to the actual base package once code lands. All ArchUnit rules assume `@AnalyzeClasses(importOptions = {ImportOption.DoNotIncludeTests.class})` and use `.allowEmptyShould(true)` where the target pattern may not yet exist — appropriate for a codebase being built from zero.

---

## Backend — Core

### Cycles

| Rule | Tool | Intent | Target File | Sketch | Notes | Implemented In |
|---|---|---|---|---|---|---|
| `no_cyclic_dependencies` | ArchUnit | No cyclic package/class dependencies within the application | — | `SlicesRuleDefinition.slices().matching("<base>.(*)..").should().beFreeOfCycles()` | Replace `<base>` with the app base package | — (not generated: no JVM build file) |
| `no_intra_layer_cycles` | ArchUnit | No cycles between classes inside the same layer | — | `SlicesRuleDefinition.slices().matching("..service.(*)..").should().beFreeOfCycles().allowEmptyShould(true)` — repeat per layer (`service`, `controller`, `repository`) | `.allowEmptyShould(true)` for layers with no sub-packages | — (not generated: no JVM build file) |

### Layer Boundaries

| Rule | Tool | Intent | Target File | Sketch | Notes | Implemented In |
|---|---|---|---|---|---|---|
| `layer_access_rules` | ArchUnit | Controllers→services, services→repositories; repositories don't access upward | — | `layeredArchitecture().consideringAllDependencies().layer("Controller").definedBy("..controller..").layer("Service").definedBy("..service..").layer("Repository").definedBy("..repository..").whereLayer("Controller").mayNotBeAccessedByAnyLayer().whereLayer("Service").mayOnlyBeAccessedByLayers("Controller").whereLayer("Repository").mayOnlyBeAccessedByLayers("Service")` | Single-hierarchy app → `consideringAllDependencies()` is correct | — (not generated: no JVM build file) |
| `no_business_logic_in_controllers` | ArchUnit | Controllers must not depend on repository/persistence classes | — | `noClasses().that().resideInAPackage("..controller..").should().dependOnClassesThat().resideInAnyPackage("..repository..","..persistence..","..dao..")` | Stricter anti-pattern check complementing layering | — (not generated: no JVM build file) |
| `no_transactional_outside_service_controller` | ArchUnit | `@Transactional` must not appear on controllers | — | `noClasses().that().resideInAPackage("..controller").should().beAnnotatedWith(org.springframework.transaction.annotation.Transactional.class).allowEmptyShould(true)` | Transaction boundary belongs in service layer | — (not generated: no JVM build file) |
| `no_transactional_outside_service_domain` | ArchUnit | `@Transactional` must not appear on domain/entity classes | — | `noClasses().that().resideInAnyPackage("..entity","..model").should().beAnnotatedWith(org.springframework.transaction.annotation.Transactional.class).allowEmptyShould(true)` | Domain must stay persistence-agnostic | — (not generated: no JVM build file) |

### Naming Conventions

| Rule | Tool | Intent | Target File | Sketch | Notes | Implemented In |
|---|---|---|---|---|---|---|
| `naming_conventions_controller` | ArchUnit | Classes in `controller` end with `Controller` | — | `classes().that().resideInAPackage("..controller").and().areTopLevelClasses().and().areNotInterfaces().should().haveSimpleNameEndingWith("Controller").allowEmptyShould(true)` | Non-trailing `..controller` + `.areTopLevelClasses()` to avoid `controller.dto` false positives | — (not generated: no JVM build file) |
| `naming_conventions_service` | ArchUnit | Classes in `service` end with `Service` | — | `classes().that().resideInAPackage("..service").and().areTopLevelClasses().and().areNotInterfaces().should().haveSimpleNameEndingWith("Service").allowEmptyShould(true)` | — | — (not generated: no JVM build file) |
| `naming_conventions_repository` | ArchUnit | Types in `repository` end with `Repository` | — | `classes().that().resideInAPackage("..repository").and().areTopLevelClasses().should().haveSimpleNameEndingWith("Repository").allowEmptyShould(true)` | Repositories are Spring Data interfaces → keep `.allowEmptyShould(true)` | — (not generated: no JVM build file) |
| `no_generic_exception_throws` | ArchUnit | No method declares `throws Exception`/`Throwable` | — | `GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS` (or custom `ArchCondition`); `.allowEmptyShould(true)` | Use predefined constant | — (not generated: no JVM build file) |

### Class Containment

| Rule | Tool | Intent | Target File | Sketch | Notes | Implemented In |
|---|---|---|---|---|---|---|
| `no_classes_in_default_package` | ArchUnit | No class in the unnamed (default) package | — | `noClasses().should().haveNameMatching("[^.]+").as("No class should reside in the default (unnamed) package")` | `resideInAPackage("default package")` is invalid — match dot-less FQNs | — (not generated: no JVM build file) |
| `class_containment_service` | ArchUnit | `*Service` classes reside in `..service..` | — | `classes().that().haveSimpleNameEndingWith("Service").and().areTopLevelClasses().should().resideInAPackage("..service..").allowEmptyShould(true)` | Inverse of naming convention | — (not generated: no JVM build file) |
| `class_containment_controller` | ArchUnit | `*Controller` classes reside in `..controller..` | — | `classes().that().haveSimpleNameEndingWith("Controller").and().areTopLevelClasses().should().resideInAPackage("..controller..").allowEmptyShould(true)` | — | — (not generated: no JVM build file) |
| `entities_only_in_entity_packages` | ArchUnit | `@Entity` classes reside in `..entity..` | — | `classes().that().areAnnotatedWith(jakarta.persistence.Entity.class).should().resideInAPackage("..entity..").allowEmptyShould(true)` | Use `jakarta.persistence` for Spring Boot 3 | — (not generated: no JVM build file) |
| `configuration_classes_in_config_package` | ArchUnit | `@Configuration` classes reside in `..config..` | — | `classes().that().areAnnotatedWith(org.springframework.context.annotation.Configuration.class).should().resideInAPackage("..config..").allowEmptyShould(true)` | — | — (not generated: no JVM build file) |

### Dependency Injection

| Rule | Tool | Intent | Target File | Sketch | Notes | Implemented In |
|---|---|---|---|---|---|---|
| `no_field_injection` | ArchUnit | No `@Autowired`/`@Inject` field injection; constructor injection only | — | `noFields().that().areDeclaredInClassesThat().areTopLevelClasses().should().beAnnotatedWith(org.springframework.beans.factory.annotation.Autowired.class).allowEmptyShould(true)` | Do NOT use `NO_CLASSES_SHOULD_USE_FIELD_INJECTION` (catches `@Value`); add a sibling rule for `@Inject` if JSR-330 used | — (not generated: no JVM build file) |

### Code Quality

| Rule | Tool | Intent | Target File | Sketch | Notes | Implemented In |
|---|---|---|---|---|---|---|
| `no_system_out` | ArchUnit | No `System.out`/`System.err` in production; use logging | — | `GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS` | Aligns with PRD audit-logging requirement | — (not generated: no JVM build file) |
| `no_test_imports_in_production` | ArchUnit | Production classes don't import test frameworks | — | `noClasses().that().resideOutsideOfPackage("..test..").should().dependOnClassesThat().resideInAnyPackage("org.junit..","org.mockito..","org.testng..")` | Requires `ImportOption.DoNotIncludeTests` in `@AnalyzeClasses` | — (not generated: no JVM build file) |

### JPA / Persistence

| Rule | Tool | Intent | Target File | Sketch | Notes | Implemented In |
|---|---|---|---|---|---|---|
| `no_entity_in_controllers` | ArchUnit | Controllers must not depend on `@Entity` classes (DTOs at API boundary) | — | `noClasses().that().resideInAPackage("..controller..").should().dependOnClassesThat().areAnnotatedWith(jakarta.persistence.Entity.class).allowEmptyShould(true)` | Prevents accidental data exposure (e.g. password_hash leakage — see PRD Story 8) | — (not generated: no JVM build file) |
| `repositories_must_be_interfaces` | ArchUnit | Classes in `..repository` are interfaces | — | `classes().that().resideInAPackage("..repository").and().areTopLevelClasses().should().beInterfaces().allowEmptyShould(true)` | Add `.and().haveSimpleNameNotEndingWith("Impl")` if custom-fragment pattern used | — (not generated: no JVM build file) |

### Spring Annotations

| Rule | Tool | Intent | Target File | Sketch | Notes | Implemented In |
|---|---|---|---|---|---|---|
| `controllers_must_be_annotated` | ArchUnit | Classes in `..controller` carry `@RestController`/`@Controller` | — | `classes().that().resideInAPackage("..controller").and().areTopLevelClasses().and().areNotInterfaces().should().beAnnotatedWith(org.springframework.web.bind.annotation.RestController.class).orShould().beAnnotatedWith(org.springframework.stereotype.Controller.class).allowEmptyShould(true)` | Non-trailing `..controller` to skip `controller.dto` | — (not generated: no JVM build file) |

---

## Frontend — Core

### Cycles

| Rule | Tool | Intent | Target File | Sketch | Notes | Implemented In |
|---|---|---|---|---|---|---|
| `no_circular_module_deps` | depcruise | No circular dependency anywhere in the module graph | `.dependency-cruiser.cjs` | `{ name: "fe-no-circular", severity: "error", from: {}, to: { circular: true } }` | Built-in cycle detection; no path patterns needed. Applies once `src/` exists | `.dependency-cruiser.cjs` |

---

## For Consideration

_These rules are not yet enforced. Review each category and move individual rows to the relevant section above when ready, then re-run `/arch-tests-gen`._

### Cycles

| Rule | Tool | Intent | Target File | Sketch | Notes |
|---|---|---|---|---|---|
| `no_inter_aggregate_cycles` | ArchUnit | No cycles between aggregate/module root packages | — | `SlicesRuleDefinition.slices().matching("..domain.(*)..").should().beFreeOfCycles().allowEmptyShould(true)` | _Why deferred: Optional (DDD/module-structured only); this is a flat-layered project_ |

### Layer Boundaries

| Rule | Tool | Intent | Target File | Sketch | Notes |
|---|---|---|---|---|---|
| `no_upper_package_deps` | ArchUnit | No dependency on classes in parent packages | — | `DependencyRules.NO_CLASSES_SHOULD_DEPEND_UPPER_PACKAGES` | _Why deferred: Optional (Severity low); false alarms if any layer uses sub-packages (`service.impl`, `controller.dto`). Verify flat single-level packages before enabling_ |
| `service_depends_on_repository_interfaces` | ArchUnit | Services depend on repository interfaces, not `*Impl` | — | `noClasses().that().resideInAPackage("..service..").should().dependOnClassesThat().haveSimpleNameEndingWith("Impl").and().resideInAPackage("..repository..").allowEmptyShould(true)` | _Why deferred: Optional; Spring Data repositories are interfaces with no `impl` sub-package_ |
| `no_validation_in_repositories` | LLM | Repositories contain no input validation logic | `src/main/java/**/repository/**/*.java` | Check that repository/data-access types contain only persistence operations, no validation | _Why deferred: LLM-reviewed → always For Consideration_ |

### Naming Conventions

| Rule | Tool | Intent | Target File | Sketch | Notes |
|---|---|---|---|---|---|
| `exception_naming_convention` | ArchUnit | `Exception` subclasses named `*Exception` and vice-versa | — | `classes().that().areAssignableTo(Exception.class).should().haveSimpleNameEndingWith("Exception").allowEmptyShould(true)` | _Why deferred: Severity low_ |
| `inheritance_naming` | ArchUnit | Implementations named after the interface they implement | — | `classes().that().implement(SomeInterface.class).and().areTopLevelClasses().should().haveSimpleNameEndingWith("SomeInterface").allowEmptyShould(true)` | _Why deferred: Optional; include only if a consistent interface-named-impl pattern emerges_ |

### Class Containment

| Rule | Tool | Intent | Target File | Sketch | Notes |
|---|---|---|---|---|---|
| `entities_have_required_annotations` | ArchUnit | `@Entity` classes also carry `@Table` | — | `classes().that().areAnnotatedWith(jakarta.persistence.Entity.class).should().beAnnotatedWith(jakarta.persistence.Table.class).allowEmptyShould(true)` | _Why deferred: Severity low_ |

### Dependency Injection

| Rule | Tool | Intent | Target File | Sketch | Notes |
|---|---|---|---|---|---|
| `no-new-inside-service` | ArchUnit | No `new` of service/repository classes inside services | — | Custom `ArchCondition` flagging constructor calls to `..service`/`..repository` types from `..service`; `.and().areTopLevelClasses()` | _Why deferred: Severity low_ |

### Code Quality

| Rule | Tool | Intent | Target File | Sketch | Notes |
|---|---|---|---|---|---|
| `utility_classes_private_constructor` | ArchUnit | Static-only utility classes are final with private constructor | — | `classes().that().haveSimpleNameEndingWith("Util").or().haveSimpleNameEndingWith("Utils").should().haveOnlyPrivateConstructors().allowEmptyShould(true)` | _Why deferred: Severity low_ |
| `no_dependency_on_deprecated` | ArchUnit | No production dependency on `@Deprecated` types | — | `noClasses().should().dependOnClassesThat().areAnnotatedWith(Deprecated.class).allowEmptyShould(true)` | _Why deferred: Severity low_ |

### JPA / Persistence

| Rule | Tool | Intent | Target File | Sketch | Notes |
|---|---|---|---|---|---|
| `annotation_gated_access` | ArchUnit | `EntityManager` only accessed by `@Transactional` callers | — | `classes().that().areAssignableTo(jakarta.persistence.EntityManager.class).should().onlyHaveDependentClassesThat().areAnnotatedWith(org.springframework.transaction.annotation.Transactional.class)` | _Why deferred: Severity low; app likely uses Spring Data repositories, not raw EntityManager_ |

### Test Isolation

| Rule | Tool | Intent | Target File | Sketch | Notes |
|---|---|---|---|---|---|
| `no-logic-in-test-helpers` | ArchUnit | Shared test helpers contain no assertion logic | — | Custom check: classes in `..testsupport`/`..fixtures` must not call assertion APIs; `.allowEmptyShould(true)` | _Why deferred: Severity low_ |
| `test-classes-in-test-source` | ArchUnit | `@Test` classes live under test source root | — | `classes().that().containAnyMethodsThat(areAnnotatedWith(Test.class)).should().resideOutsideOfPackage("<main-base>..")` | _Why deferred: Severity low_ |

### Layer Boundaries (Frontend)

| Rule | Tool | Intent | Target File | Sketch | Notes |
|---|---|---|---|---|---|
| `no_direct_http_in_components` | depcruise | UI components must not import HTTP clients / `src/api` directly | `.dependency-cruiser.js` | `from.path: ^src/(components\|pages\|views\|features)`; `to`: npm `^(axios\|node-fetch\|got\|ky\|cross-fetch\|superagent)$` **and** `^src/api/` (exclude `react-query`/`swr`) | _Why deferred: Severity low; also requires `src/` to exist first_ |
| `styles_not_in_logic_files` | depcruise | Service/hook/util/store files must not import CSS/SCSS | `.dependency-cruiser.js` | `from.path: ^src/(services\|hooks\|composables\|utils\|store\|api)/`; `to.path: \\.(css\|scss\|sass\|less)$`; severity warn | _Why deferred: Severity low_ |

---
_To promote a rule: copy the row into the relevant Enforced table above, then re-run `/arch-tests-gen`. No re-analysis needed._
_To re-run full analysis: `/arch-tests-plan`_
