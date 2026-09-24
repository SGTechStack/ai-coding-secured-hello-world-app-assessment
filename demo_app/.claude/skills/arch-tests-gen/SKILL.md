---
name: arch-tests-gen
description: Generate concrete ArchUnit JUnit tests and dependency-cruiser config from artifacts/arch-test-plan.md
allowed-tools: Read, Glob, Write, Bash, Skill
---

Use Glob to search for `**/artifacts/arch-test-plan.md` (excluding `node_modules`). Use the first result as the plan path. Record the directory containing `artifacts/arch-test-plan.md` as the **project root** — all subsequent relative paths (build files, source root, test output) are resolved from that root.

If no file is found, stop immediately and tell the user:

> `artifacts/arch-test-plan.md` not found. Run `/arch-tests-plan` first to generate the plan.

## Steps

### 1. Read the plan

Read the plan file located in step above to get the full list of topics, decisions, test types, sketch expressions, and notes. The plan is the sole source of truth for what to generate — do not attempt to extract code blocks from the source ADR files.

**Only process rows from Core sections** (`## Backend — Core`, `## Frontend — Core`). Skip all rows in Feature Template sections (`## Backend — Feature Template`, `## Frontend — Feature Template`) — those are generated per feature by `/arch-tests-gen-feature`. Also skip the `## For Consideration` section.

If a row has a non-empty Notes cell, use it as generation context (e.g., to narrow a slice pattern or add an exception clause) but do **not** emit it as a comment or TODO in the generated file. Notes are pre-codegen review artifacts; once generation happens they have served their purpose. Surface unresolved notes in the summary instead (see step 5).

### 2. Detect the tech stack

Search for each of these files using Glob (`**/<filename>`, excluding `node_modules`) relative to the project root determined above, then read whichever ones are found: `package.json`, `tsconfig.json`, `pom.xml`, `build.gradle`, `build.gradle.kts`, `pyproject.toml`, `go.mod`, `README.md`, `CONTEXT.md`. Use the shallowest match for each. Determine:
- **Primary language** (TypeScript, Java, Kotlin, Python, Go, …)
- **Root package / module** (for ArchUnit: the base Java package to import)
- **Source root** (e.g. `src/`, `src/main/java/`)

### 3. Write ArchUnit test classes (JVM projects only)

Skip this step entirely if the project is not JVM-based (no `pom.xml`, `build.gradle`, or `build.gradle.kts`).

For every plan row whose Test Type includes `ArchUnit`, use the **`archunit.md` reference** to generate the rule bodies for that ADR's decision and core logic. The skill outputs `static final ArchRule` fields with `@ArchTest` (no class wrapper, no imports); assemble them into the standard test class shell below and write to:

```
<build-file-dir>/src/test/java/arch/<PascalTopic><PascalSlug>ArchTest.java
```

Where `<build-file-dir>` is the directory containing the build file found in step 2 (e.g. if `pom.xml` is at `projects/foo/pom.xml`, write to `projects/foo/src/test/java/arch/…`).

Where `<PascalTopic>` and `<PascalSlug>` are the topic and ADR slug converted to PascalCase (e.g. `code-organization` → `CodeOrganization`).

Standard test class shell:

```java
package arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.tngtech.archunit.library.Architectures;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@com.tngtech.archunit.junit.AnalyzeClasses(
    packages = "<root.package>",
    importOptions = {ImportOption.DoNotIncludeTests.class}
)
public class <ClassName>ArchTest {

    <skill-generated @ArchTest fields>

}
```

- If `<root.package>` cannot be determined, leave the placeholder and note it in the summary.
- If the plan row's core logic contains no actionable rule (prose only), skip and list under "Skipped".

### 3b. Write dependency-cruiser config (frontend projects)

Skip this step entirely if no `depcruise` rows exist in the test plan.

Read the **`depcruise.md` reference** to load its DSL reference and pattern catalog into context. Then, for every plan row whose Tool is `depcruise`, use the reference's pattern catalog to generate JavaScript rule objects matching the row's intent and sketch.

Assemble all generated rule objects into the full config file shell from the `depcruise.md` reference (see its "Full config file structure" section). Determine `<srcRoot>` from the source root detected in step 2 (e.g. `src/`). Write the assembled config to:

```
<project-root>/.dependency-cruiser.cjs
```

After writing, check whether `dependency-cruiser` is listed under `devDependencies` in `package.json`. If it is absent, note in the summary: "Add dependency-cruiser to devDependencies: `npm install --save-dev dependency-cruiser`".

If `.dependency-cruiser.cjs` already exists, overwrite it — do not merge with an existing file.

### 4. Add ArchUnit dependency (JVM projects only)

After writing the test class files, ensure the ArchUnit JUnit 5 dependency is present in the build file.

**Maven (`pom.xml`)** — add inside `<dependencies>` if not already present:

```xml
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit-junit5</artifactId>
    <version>1.3.0</version>
    <scope>test</scope>
</dependency>
```

**Gradle (`build.gradle` / `build.gradle.kts`)** — add to the `dependencies` block if not already present:

```groovy
testImplementation 'com.tngtech.archunit:archunit-junit5:1.3.0'
```

```kotlin
testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
```

Search for `archunit` in the existing build file before adding — if any `archunit` dependency is already declared, skip this step and note the existing version in the summary.

### 5. Update the test plan with implementation paths

After writing all files, go back to `artifacts/arch-test-plan.md` and add an `Implemented In` column to each table:

- For **ArchUnit** rows: fill in the path of the generated Java test file (e.g. `src/test/java/arch/LayeringTest.java`).
- For **depcruise** rows: fill in `.dependency-cruiser.cjs`.
- For **LLM** or **skipped** rows: fill in `—`.

If the `Implemented In` column already exists (from a previous run), overwrite its values — do not add a duplicate column.

### 6. Print a summary

After writing all files, output:

```
Generated:
  src/test/java/arch/CodeOrganizationLayeringConventionsArchTest.java
  .dependency-cruiser.cjs  (<n> rules)

Skipped (no actionable rule):
  ADR-007 — prose only

Notes from plan (review before next run if needed):
  ADR-003 no_cycle_checks — narrow slice pattern to feature modules only to avoid flagging the intentional controller→service→repository chain

Action required:
  Add dependency-cruiser to devDependencies: npm install --save-dev dependency-cruiser   ← only if absent
```

The "Notes from plan" section lists any row whose Notes cell was non-empty, quoting the note verbatim. These are not emitted into the generated files — they are surfaced here so the user knows which sketches may need refinement.

## Quality bar

- Generated ArchUnit Java must compile against ArchUnit 1.x and JUnit 5 with no changes beyond filling in placeholders.
- Patterns must reference the actual file naming conventions, directory structure, and layer names described in the ADRs — not generic placeholders.
- Do not generate empty test files. If an ADR has only a `### CI / tooling check` section, skip it entirely.
- Each `@ArchTest` field name must be unique within its class and use only lowercase letters, digits, and underscores.
- **Naming-convention rules** must use `..layerName` (no trailing `..`) in the `that()` clause to avoid catching classes in sub-packages (e.g. `controller.dto`). Layer *definitions* in `layeredArchitecture()` still use `..layerName..`.
- **All naming and DI rules** must include `.and().areTopLevelClasses()` to exclude anonymous and inner classes, which share their enclosing class's package.
- **Optional-pattern rules** (Util, Exception subclasses, Helper/Fixture test classes, etc.) must call `.allowEmptyShould(true)` — without it the rule throws when no classes match, even though that is not a violation.
- **Deduplication:** Before generating a rule, compare its slice/condition pattern against every other rule already being generated in the same file. If the patterns are identical and the row's Notes cell says "Mark optional if duplicate" or similar, skip it and list it under "Skipped (duplicate)" in the summary. Do not emit two rules that will always produce the same violations.
- **Spring-annotations-in-domain rules** (`no_spring_annotations_in_domain` or equivalent): always exclude `..mapper..` sub-packages from the `that()` clause using `.and().resideOutsideOfPackage("..mapper..")`. MapStruct and similar code-generation tools produce `@Component`-annotated mapper implementations under `..mapper..` packages — these are infrastructure glue, not domain objects, and the annotation is auto-generated rather than a deliberate architecture violation.
