---
name: arch-tests-gen-feature
description: Generate architecture tests for a specific feature by instantiating Feature Template rows from the test plan with the feature's real package/folder names.
allowed-tools: Read, Glob, Write, Bash, Skill
---

You are generating architecture tests for a **single feature** that was just built in this session. You have context about what packages/folders were created for this feature.

---

## Step 1 — Find the test plan

Use Glob to search for `**/artifacts/arch-test-plan.md` (excluding `node_modules`). Use the first result as the plan path. Record the directory containing `artifacts/arch-test-plan.md` as the **project root**.

If no file is found, stop immediately:

> `artifacts/arch-test-plan.md` not found. Run `/arch-tests-plan` first, then `/arch-tests-gen` for core tests.

---

## Step 2 — Identify the feature

Determine the feature that was just built using your session context — you should know which packages or folders were created in this conversation.

Extract:
- **Feature name** — the module/feature identifier (e.g. `transaction`, `sloc`, `user`)
- **Backend package** — the full Java package or folder path (e.g. `local.medlog.transaction`)
- **Frontend folder** — the feature folder path if applicable (e.g. `src/features/transaction`)

If you cannot determine the feature from context, ask the user:

> Which feature should I generate architecture tests for? Provide the module/feature name (e.g. `transaction`).

If multiple new features appear to have been built in this session, prompt:

> I see multiple new features in this session: `<list>`. Which one should I generate tests for?

---

## Step 3 — Read Feature Template rows

Read `artifacts/arch-test-plan.md`. Parse only the **Feature Template** sections:
- `## Backend — Feature Template`
- `## Frontend — Feature Template`

For each row in these sections, extract:
```
rule_name    — value in the Rule column
tool         — ArchUnit, depcruise, or LLM
intent       — value in the Intent column
target_file  — value in the Target File column
sketch       — value in the Sketch column (contains <feature> placeholders)
notes        — value in the Notes column (contains substitution instructions)
```

If no Feature Template sections exist in the plan, stop:

> No Feature Template rules found in the test plan. This project may use a flat architecture where all rules are Core. Nothing to generate.

---

## Step 4 — Substitute placeholders

For each Feature Template row, replace all occurrences of `<feature>` in the sketch with the real feature values determined in Step 2:

- For **ArchUnit** rows: replace `<feature>` with the backend package (e.g. `local.medlog.transaction`)
- For **depcruise** rows: replace `<feature>` with the frontend folder name (e.g. `transaction`)
- For **LLM** rows: replace `<feature>` in the target_file globs with the real path

Also substitute in:
- Rule names: append `_<feature>` to make them unique (e.g. `layer_access_rules_transaction`)
- Class names: use `<Feature>` PascalCase in generated class names

---

## Step 5 — Generate ArchUnit test classes (JVM projects)

Skip if no ArchUnit rows exist in the Feature Template.

Use the **`archunit.md` reference** to generate rule bodies from the substituted sketches. Assemble them into the standard test class shell and write to:

```
<build-file-dir>/src/test/java/arch/<feature>/<PascalFeature><PascalCategory>ArchTest.java
```

For example, for feature `transaction` and category `Layer Boundaries`:
```
src/test/java/arch/transaction/TransactionLayerBoundariesArchTest.java
```

Standard test class shell — same as `/arch-tests-gen` but with `package arch.<feature>;`:

```java
package arch.<feature>;

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

Apply the same quality bar as `/arch-tests-gen`:
- Must compile against ArchUnit 1.x and JUnit 5
- Use actual package names from the feature, not generic placeholders
- `.areTopLevelClasses()` on naming/DI rules
- `.allowEmptyShould(true)` on optional-pattern rules
- Unique `@ArchTest` field names within the class

---

## Step 5b — Generate dependency-cruiser rules (frontend projects)

Skip if no depcruise rows exist in the Feature Template.

Use the **`depcruise.md` reference** to generate rule objects from the substituted sketches.

If `.dependency-cruiser.cjs` already exists at `<project-root>` (or the frontend sub-directory), **append** the new feature rules to the existing `forbidden` array — do NOT overwrite the file. Read the existing file first, find the `forbidden: [` array, and add the new rule objects before the closing `]`.

If `.dependency-cruiser.cjs` does not exist, note in the summary: "Run `/arch-tests-gen` first to create the base `.dependency-cruiser.cjs` with core rules, then re-run `/arch-tests-gen-feature`."

---

## Step 6 — Update the test plan

After writing all files, update `artifacts/arch-test-plan.md`:

Add or update an `Implemented In` column in the Feature Template tables. For each row that was just generated, fill in the path of the generated file. Use the feature name to distinguish from other features' implementations — if the column already has paths from a previous feature, append the new path with a comma separator.

---

## Step 7 — Print summary

```
Feature: <feature-name>
Package: <backend-package> / Frontend: <frontend-folder or "n/a">

Generated:
  src/test/java/arch/<feature>/<ClassName>ArchTest.java
  .dependency-cruiser.cjs  (appended <n> rules for <feature>)

Skipped:
  <any skipped rows and why>

Run tests:
  ./mvnw test -Dtest="arch.<feature>.**"
  npx depcruise --config .dependency-cruiser.cjs --output-type err src
```
