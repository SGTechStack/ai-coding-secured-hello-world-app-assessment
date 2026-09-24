---
name: mutation-testing
description: Runs mutation testing against the codebase to find surviving mutations, weak assertions, and missing coverage. Detects the project mutation tool automatically (PIT for Java, Stryker for JS/TS, mutmut or Cosmic Ray for Python, Stryker.NET for .NET). Use after the /do-work feedback loop is clean and before committing a slice, scoped to changed files only. Full codebase runs belong in a nightly CI schedule.
---

# Mutation Testing

Run mutation testing to validate that tests are load-bearing, not just coverage-complete. Run once after the feedback loop is clean and before committing — scoped to changed files only so it stays fast per slice. Full codebase runs belong in a nightly CI schedule.

## Quick start

| Invocation | Behaviour |
|------------|-----------|
| `/mutation-testing` | Changed files only, 70% threshold |
| `/mutation-testing --scope=all` | Full codebase, for release gates |
| `/mutation-testing --threshold=80` | Stricter threshold |
| `/mutation-testing --target=<path>` | Specific module or file |

All options: `--scope=all|changed|module|path|pattern`, `--target=<path-or-pattern>`, `--threshold=<percent>` (default: 70), `--tool=auto|pit|stryker|mutmut|cosmic-ray|dotnet-stryker|custom`, `--command=<cmd>`, `--config=<path>`, `--format=md|json|html` (default: md), `--output=<path>` (default: `artifacts/mutation-testing/`), `--history`, `--timeout=<seconds>`.

## Workflow

### 1. Detect ecosystem

Detect the project root and mutation tool from existing config files. Abort if none found — do not install or configure a tool. Record the discovery summary (project root, ecosystem, tool, tool config, baseline command, mutation command, target scope, threshold, output path) before running.

| Signal | Tool |
|--------|------|
| `pitest` plugin/config in `pom.xml`, `build.gradle`, or `build.gradle.kts` | PIT (JVM) |
| `stryker.conf.*`, `stryker.config.*`, or Stryker in `package.json` | StrykerJS |
| `mutmut_config` or mutmut in `pyproject.toml` | mutmut |
| `cosmic-ray.toml` or cosmic-ray in `pyproject.toml` | Cosmic Ray |
| `.sln`, `.csproj`, or `.config/dotnet-tools.json` with Stryker.NET | Stryker.NET |
| `--command=<cmd>` provided | Custom |

### 2. Run baseline tests

Prefer project-defined scripts (`test`, `test:unit`) over ecosystem fallbacks (`mvn test`, `./gradlew test`, `npm test`, `pytest`, `dotnet test`). Abort if no baseline command can be determined, or if baseline tests fail.

### 3. Resolve scope and run

Default scope is `--scope=changed`: mutate only files modified on the current branch. Use `--scope=all` for full release gates.

Run using existing project config only. Do not modify build files or mutation configs.

See [references/tool-adapters.md](./references/tool-adapters.md) for tool-specific commands and rules.

### 4. Locate reports

Find reports from tool defaults and config. Prefer structured formats (JSON, XML, SQLite) over HTML. Common locations:
- PIT: `target/pit-reports/`, `build/reports/pitest/`
- StrykerJS: `reports/mutation/`
- mutmut: `.mutmut-cache/`
- Stryker.NET: `StrykerOutput/`

### 5. Normalize results

Normalize tool output into: Tool, Target, Total Mutants, Killed, Survived, No Coverage, Timeout, Skipped, Mutation Score.

### 6. Classify findings

- **F01 No coverage** — no test reaches the code. Add a test that exercises the path.
- **F02 Weak assertion** — test runs code but does not verify the changed behaviour. Strengthen assertions.
- **F03 Boundary gap** — conditional, comparison, or operator mutant survives. Add boundary-value and negative-path tests.
- **F04 Timeout** — possible infinite loop or resource leak. Investigate before increasing timeouts. Do not conflate with equivalent mutations.
- **F05 Equivalent** — mutation does not change semantic behaviour. Confirm manually before excluding.
- **F06 Generated code** — boilerplate, migrations, or DTOs with no custom logic. Exclude or cover indirectly.

See [references/fix-patterns.md](./references/fix-patterns.md) for fix patterns per mutation class.

### 7. Write report

Write a severity-ranked report to `artifacts/mutation-testing/{feature}-mutation.md`. Commit alongside the code.

See [references/report-template.md](./references/report-template.md) for the full report template.

## Severity

| Severity | Applies to | Action |
|----------|------------|--------|
| Critical | Auth, permissions, audit, cryptography, core business invariants | Fix before release |
| High | Core domain logic, public API, persistence, validation | Fix before sign-off |
| Medium | Adapters, mappers, utilities, secondary workflows | Add tests this sprint |
| Low | Confirmed equivalents, generated code, framework glue | Exclude narrowly or document |

Use domain glossary, ADRs, and architecture docs to identify critical modules. Do not rely on package names alone.

## Rules

- Default scope is `--scope=changed`. Full runs are CPU-intensive; reserve for release gates.
- Abort if baseline tests fail before running mutations.
- Never exclude a survivor as equivalent without manual inspection.
- Do not modify build files, mutation configs, or package manifests.
- Prefer structured report parsing over HTML scraping.
- Map severity from actual business risk, not naming conventions alone.
