---
name: arch-tests-plan
description: Analyse the project and produce artifacts/arch-test-plan.md — discovering the architecture style, applying standard and ADR-derived rules, and mapping each to a concrete test row.
allowed-tools: Read, Glob, Write, Skill, AskUserQuestion
---

Walk the steps below to analyse the project's structure and write `artifacts/arch-test-plan.md` directly — no intermediate file is required.

Keep a working rule set in context divided into two buckets:
- **Enforced** — will appear in the main test-plan tables; `/arch-tests-gen` generates runnable tests for these.
- **For Consideration** — will appear in a review appendix at the bottom; `/arch-tests-gen` ignores them.

Severity mapping (applies to every rule):
- `Severity: high` in the standards-rules catalogue → **Enforced**.
- `Severity: low`, or any LLM-reviewed rule → **For Consideration**.

---

## Step 1 — Discover the project

This step determines: architecture style, dependency direction, stack/frameworks, and any existing layers or modules. Gather all of this before moving on.

### 1a — Scan documentation

Search for project docs that describe the architecture, stack, or conventions:

```
README.md, CONTEXT.md, ARCHITECTURE.md
docs/architecture*.md
decisions/**/*.md, adr/**/*.md, docs/adr/**/*.md
docs/decisions/**/*.md, doc/adr/**/*.md, architecture/**/*.md
```

Read any files found and extract: intended architecture style, stack/frameworks, layer/module conventions, dependency direction, and any other structural decisions. Keep these as **doc context** for later use.

### 1b — Scan code structure

Find the source root:
- JVM: `**/src/main/java/**/*.java` (shallowest match)
- TypeScript/JavaScript: `**/src/**/*.ts` or `**/src/**/*.js`; if no `src/`, scan root

List the immediate sub-packages/folders under the root application package.

### 1c — Determine architecture style

Map the observed structure to a style:

| Signal folders / packages at root level | Classified style |
|---|---|
| `controller`, `service`, `repository`, `entity`, `persistence`, `web` | **flat-layered** — a single set of layers (one `controller` → `service` → `repository` chain); no per-feature modules |
| `domain`, `application`, `infrastructure`, `usecase` | **onion / clean** |
| `core` + `ports` + `adapters` | **hexagonal** |
| Business-domain names (`order`, `user`, `product`, `billing`) with no layer names — each containing their own sub-layers | **modular monolith / vertical slices** |
| Single service whose internals match any of the above | **microservice** (note internal style as well) |
| No recognisable pattern | **unknown** |

**Resolution priority:**

1. **Code structure** — if the layout clearly matches one style, use it.
2. **Doc context** — if code is ambiguous but docs state the intended style, use that. Record as `Declared style: <style> (from <doc-name>)`.
3. **Ask the user** — if neither code nor docs give a clear answer, **you MUST ask the user**. Present the styles as options. If the user is unsure, briefly explain each.

**IMPORTANT: Never skip the user prompt.** Even if the codebase is completely empty with zero source files, you must still ask the user which style they intend. Do NOT jump straight to writing the minimal plan.

Only if the user explicitly responds that there is no intended structure yet, stop and write a minimal `artifacts/arch-test-plan.md`:

> Architecture style: unknown — no clear package structure detected and no intended style declared. Define a structure first (an ADR), then re-run `/arch-tests-plan`.

### 1d — Determine dependency direction

Use the same priority (code → docs → ask user):

- **Layered (down):** controller imports service; service imports repository. Nothing flows upward.
- **Onion / Clean / Hexagonal (in):** Domain imports nothing external. Infrastructure imports domain.
- Cross-check against the declared style:

| Declared style | Observed imports | Conclusion |
|---|---|---|
| onion/clean/hexagonal | domain imports framework/db | Accidentally coupled — treat as flat-layered, note discrepancy |
| flat-layered | domain imports nothing, infra imports domain | May be evolving toward onion — record |
| (any) | consistent with declared style | Confirmed |
| (any) | too few files to determine | Use doc/user-provided direction; record as `(declared)` |

### 1e — Detect frameworks

Check build files (`pom.xml`, `build.gradle`, `package.json`) and source for:

| Framework | Detection signals |
|---|---|
| **Spring / Spring Boot** | `spring-boot-starter` in pom.xml / build.gradle; `@SpringBootApplication` in source |
| **JPA / Hibernate** | `spring-boot-starter-data-jpa` or `hibernate-core` in dependencies; `@Entity` in source |
| **MapStruct** | `mapstruct` in dependencies; `@Mapper` annotations in source |
| **JUnit** | `junit-jupiter` or `junit` in test dependencies |
| **Frontend framework** | `react`, `vue`, `angular`, `svelte`, `next`, `nuxt` in `package.json` |

### 1f — Detect frontend structure (if frontend present)

A frontend is present if `package.json` lists a frontend framework, or `src/components/`, `src/pages/`, `src/views/`, or `src/features/` directories exist, or a `vite.config.*` / `next.config.*` / `webpack.config.*` file exists.

If detected, classify:

| Observed directories | Structure type |
|---|---|
| `features/` or `modules/` with domain sub-directories | feature-based |
| `pages/` or `views/` alongside `components/` | page-based |
| `components/` only | flat |
| Monorepo packages with own `src/` | package-based |

Note: `shared/`/`common/`/`utils/` dirs, `store/`/`redux/` dirs, `next.config.*` presence, `index.ts` barrels in features.

Record: `Confirmed style`, `Dependency direction`, `Detected frameworks`, `Frontend structure (or "none")`.

---

## Step 2 — Select rules from catalogue

Invoke the `standards-rules.md` file  to load the rule catalogue. Then include rules from each applicable section. The catalogue is the **single source of truth** for rule names, intents, severities, and notes — do not maintain separate rule lists here.

For every rule in an applicable section:
- If `Severity: high` → **Enforced**.
- If `Severity: low` → **For Consideration**.
- If `Optional: yes` → include only if the stated condition is met; skip otherwise.
- If `Languages` field does not include a project language → skip.
- LLM-reviewed rules → always **For Consideration**.
- Never duplicate a rule already added from a previous section.

### Which sections to include

Apply sections in this order:

1. **Universal (backend)** — always include for any backend project. All sub-groups: Cycles, Naming, Anti-patterns.

2. **By architectural style** — include the section matching the confirmed style from Step 1c (Layered, Onion/Clean/Hexagonal, or Modular monolith). For microservices, use whichever matches the internal style.

3. **By framework (backend)** — for each framework detected in Step 1e, include its section (Spring/Spring Boot, JPA/Hibernate, JUnit). Respect `Optional` conditions (e.g. `mappers_in_mapper_packages` only if MapStruct detected).

4. **Frontend — Universal** — always include if a frontend was detected in Step 1f.

5. **Frontend — Module/feature boundaries** — include rules whose conditions are met based on the observed frontend structure (e.g. `no_feature_cross_imports` only if `features/` directory exists). The catalogue states the inclusion condition for each rule.

6. **Frontend — Layer boundaries** — same: include rules whose conditions are met.

7. **Frontend — By framework** — include if the specific frontend framework is detected (e.g. Next.js rules only if `next.config.*` exists).

8. **LLM-reviewed** — include rules whose `Languages` match. Always place in **For Consideration**. Include `Optional: yes` LLM rules only when their condition is met.

---

## Step 3 — Read ADRs and derive additional rules

### 3a — Find ADR files

Re-use any ADR files discovered in Step 1a. If none were found, search now using the same glob patterns, plus a fallback for files containing `adr`, `decision`, or `0001`–`0099`.

If no ADR files found: `No ADRs found — skipping ADR-derived rules.` Continue to Step 4.

### 3b — Parse each ADR

For each ADR, identify:

1. **The decision** — the architectural choice.
2. **The scope** — which packages, modules, layers, or file patterns.
3. **Whether already covered** — check rules from Step 2. If covered, note it and skip.
4. **Rule derivability:**
   - **Yes** → derive a rule using the mapping table below.
   - **Possibly** → add as LLM-reviewed in **For Consideration**.
   - **No** (process, team norms) → skip.

**Decision → rule mapping:**

| ADR decision type | Rule to derive |
|---|---|
| "Module X must not depend on module Y" | `no_forbidden_package_deps` scoped to those packages |
| "All HTTP calls go through a service layer" | `no_direct_http_in_components` (frontend) or `no_business_logic_in_controllers` (backend) |
| "Use constructor injection only" | `no_field_injection` |
| "Transactions managed at service boundary" | `no_transactional_outside_service` |
| "Domain must not import infrastructure" | `no_domain_infrastructure_imports` |
| "Cross-module via shared/ or API barrel" | `no_feature_cross_imports` + `shared_no_feature_imports` |
| "Every entity must carry @Table" | `entities_have_required_annotations` |
| "Controllers must not access repos" | `no_business_logic_in_controllers` |
| "Services end in *Service" | `naming_conventions` scoped to that layer |
| Not mappable to a structural rule | LLM-reviewed rule with scope = relevant file globs |

### 3c — Observe codebase conventions

Read enough production code to spot patterns the team follows but hasn't documented:

| Observed pattern | Rule to generate |
|---|---|
| Entities extend a base class (e.g. `BaseEntity`) | `inheritance_naming` |
| Types always carry a required annotation | containment + annotation check |
| Specific library forbidden in a layer | `no_forbidden_package_deps` for that package |

### 3d — Severity for ADR-derived rules

- ADR uses "must", "shall", "required", "forbidden" → `high` → **Enforced**.
- ADR uses "should", "prefer", "recommended", or is draft → `low` → **For Consideration**.
- LLM-reviewed ADR rules → always **For Consideration**.

---

## Step 4 — Classify rules and write the plan

### 4a — Load test-writing references

Read the `archunit.md` and `depcruise.md` references (in the same directory as this skill) to load DSL references for writing sketches.

### 4b — Assign enforcement type

For each **Enforced** rule:

| Question | Type |
|---|---|
| JVM structural rule (layer/package, class constraints, annotations)? | ArchUnit |
| TypeScript/JavaScript import or module-boundary rule? | depcruise |
| Semantic but bounded scope? | LLM |
| Otherwise | skip (note in summary) |

### 4c — Classify Core vs Feature

- **Core** — project-wide, no specific feature/module names needed. Concrete sketch, generated once by `/arch-tests-gen`.
- **Feature** — must reference a specific feature/module. Uses `<feature>` placeholder, generated per feature by `/arch-tests-gen-feature`.

| Style | Core | Feature |
|---|---|---|
| Flat-layered | Almost all | Rarely any |
| Modular monolith | Cycles, naming, containment, DI, default-package | Layer boundaries per module, module isolation |
| Onion / Clean | Domain purity, naming, cycles | Per-module if vertical modules exist |
| Hexagonal | Port/adapter constraints, naming, cycles | Per-adapter or per-module |

**Modular monolith:** Do NOT emit one row per existing module. Emit a single **Feature** row with `<feature>` placeholders. `/arch-tests-gen-feature` instantiates it per module.

**Sketch guidelines:**
- ArchUnit: use DSL from `archunit.md` reference. Apply safe-defaults (trailing-`..` pitfall, `.areTopLevelClasses()`, `.allowEmptyShould(true)`). Respect `**plan-arch-tests warning:**` and `**Safe-defaults:**` fields from the catalogue.
- depcruise: compact sketch with key `from`/`to` matchers. Use `<br>` for multiple pairs.
- LLM: one sentence describing what to check.

### 4d — Write artifacts/arch-test-plan.md

Write to `artifacts/arch-test-plan.md` in the project root. Overwrite if it exists.

```markdown
# Test Plan

Generated: <YYYY-MM-DD>
Stack: <language(s)> / <framework(s)> / <build tool>
Architecture style: <confirmed style>
Dependency direction: <down / in / inconsistent>
Frontend: <framework + structure type, or "none detected">

---

## Backend — Core

### <Category>

| Rule | Tool | Intent | Target File | Sketch | Notes |
|---|---|---|---|---|---|
| `<rule-name>` | ArchUnit | <intent> | — | `<concrete rule chain>` | <caveat or —> |

(Repeat ### per category. Omit section if no backend Core rules.)

---

## Backend — Feature Template

### <Category>

| Rule | Tool | Intent | Target File | Sketch | Notes |
|---|---|---|---|---|---|
| `<rule-name>` | ArchUnit | <intent> | — | `<sketch with <feature> placeholder>` | Replace `<feature>` with module root package |

(Omit if no Feature rules — e.g. flat-layered projects.)

---

## Frontend — Core

(Same structure. Omit if no frontend detected.)

---

## Frontend — Feature Template

(Same structure. Omit if no frontend Feature rules.)

---

## For Consideration

_These rules are not yet enforced. Review each category and move individual rows to the relevant section above when ready, then re-run `/arch-tests-gen`._

### <Category>

| Rule | Tool | Intent | Target File | Sketch | Notes |
|---|---|---|---|---|---|
| `<rule-name>` | ArchUnit | <intent> | — | `<sketch>` | _Why deferred: <brief reason>_ |

(Omit if all rules are Enforced.)

---
_To promote a rule: copy the row into the relevant Enforced table above, then re-run `/arch-tests-gen`. No re-analysis needed._
_To re-run full analysis: `/arch-tests-plan`_
```

**Category names** (assign each rule to the best fit; omit empty categories):

| Category | Typical rules |
|---|---|
| Cycles | `no_cyclic_dependencies`, `no_circular_module_deps`, `no_intra_layer_cycles` |
| Layer Boundaries | `layer_access_rules`, `no_domain_infrastructure_imports`, `no_forbidden_package_deps`, `no_business_logic_in_controllers`, `no_transactional_outside_service` |
| Module Boundaries | `no_feature_cross_imports`, `shared_no_feature_imports`, `no_cross_page_imports` |
| Naming Conventions | `naming_conventions`, `exception_naming_convention`, `no_generic_exception_throws` |
| Class Containment | `class_containment_checks`, `no_classes_in_default_package`, `entities_only_in_entity_packages`, `mappers_in_mapper_packages`, `configuration_classes_in_config_package` |
| Dependency Injection | `no_field_injection`, `no-new-inside-service` |
| Code Quality | `utility_classes_private_constructor`, `no_test_imports_in_production`, `no_system_out` |
| JPA / Persistence | `entities_have_required_annotations`, `no_entity_in_controllers`, `repositories_must_be_interfaces` |
| Spring Annotations | `controllers_must_be_annotated` |
| HTTP / External Calls | `no_direct_http_in_components`, `no_server_imports_in_client_components` |
| State Management | `no_store_in_non_hook_files` |
| Public API / Barrels | `barrel_only_public_api` |
| ADR-derived | Rules derived from ADRs that don't fit an existing category |

Pre-split multi-rule entries into individual rows (e.g. `naming_conventions_controller`, `naming_conventions_service`).

---

## Step 5 — Print summary

```
Written: artifacts/arch-test-plan.md
  Stack: <language(s)> / <framework(s)>
  Architecture style: <confirmed style>
  Dependency direction: <down / in / inconsistent>
  Frontend: <framework + structure, or "none detected">
  Core rules: <N>  (ArchUnit: <n>  depcruise: <n>  LLM: <n>)
  Feature template rules: <N>  (ArchUnit: <n>  depcruise: <n>  LLM: <n>)
  For Consideration: <N>
  Skipped (no actionable artefact): <n>
  Categories: <list>
```

Then prompt the user:

> Review `artifacts/arch-test-plan.md`. Delete any **For Consideration** categories you don't want, or move individual rules up to the main tables. When ready:
> 1. Run `/arch-tests-gen` to generate Core test files (run once at project setup).
> 2. After building each feature, run `/arch-tests-gen-feature` to generate tests for that feature.
