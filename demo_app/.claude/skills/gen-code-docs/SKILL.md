---
name: gen-code-docs
description: "Generates API source code documentation by extracting TSdoc and Javadoc comments from the codebase. Produces HTML documentation sites for internal module APIs."
argument-hint: "[project-root]"
---

# Generate Code Documentation

## User Input

```text
$ARGUMENTS
```

The argument is the **project root** to document. If omitted, default to cwd.

---

## What This Skill Does

Detect the project's tech stack from manifest files, run the appropriate documentation generator, and produce HTML API reference docs.

## Output

| Stack | Generator | Output |
|-------|-----------|--------|
| TypeScript (`tsconfig.json`) | TypeDoc | `docs/tsdoc/` |
| Java (`pom.xml` / `build.gradle(.kts)`) | Javadoc | `docs/javadoc/` |

## Procedure

1. **Detect stack** -- look for `tsconfig.json`, `pom.xml`, `build.gradle(.kts)` in the project root. If both TypeScript and Java sources exist, generate both.

2. **TypeDoc (TypeScript)** -- if `tsconfig.json` found:
   - Install if missing: `npm install --save-dev typedoc`
   - If no `typedoc.json` exists, create one:
     ```json
     {
       "entryPoints": ["src"],
       "entryPointStrategy": "expand",
       "out": "docs/tsdoc",
       "tsconfig": "tsconfig.json",
       "exclude": ["**/*.test.ts", "**/*.spec.ts", "**/__tests__/**"],
       "excludePrivate": true,
       "excludeInternal": true
     }
     ```
   - Run `npx typedoc`. Timeout: 5 minutes.
   - For monorepos, generate per-package docs separately.

3. **Javadoc (Java)** -- if Java manifest found:
   - Maven: `mvn javadoc:javadoc` (output to `docs/javadoc/`)
   - For multi-module Maven projects, use `mvn javadoc:aggregate` instead.
   - Gradle: `./gradlew javadoc`, then copy `build/docs/javadoc/` to `docs/javadoc/`
   - Timeout: 5 minutes.

4. **Verify & report** -- confirm each output directory contains `index.html`. Output a summary with generator status, output paths, and how to view (e.g. `open docs/tsdoc/index.html`).

## Rules

- **Minimal modification** -- only create `typedoc.json` if none exists. Never modify source files or build files.
- **Output isolation** -- all docs go under `docs/tsdoc/` or `docs/javadoc/`.
- **Fail gracefully** -- if a generator fails, report the error and continue with others.
- **No workarounds** -- only run the standard generator commands (`npx typedoc`, `mvn javadoc:javadoc`, `./gradlew javadoc`). If the command fails (missing dependencies, unresolvable parent POM, missing JDK, etc.), report the exact error to the user. Do NOT attempt to fix the build, create stub classes, manually resolve dependencies, invoke `javadoc` directly, exclude files, or work around the failure in any way.
