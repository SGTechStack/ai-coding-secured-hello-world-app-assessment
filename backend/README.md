# demo-backend

Spring Boot backend

## Architecture

This project uses a modular monolith structure with DDD-inspired, domain-first package
boundaries.

```text
org.eds.demo
  <domain>
    api
    application
    domain
    infrastructure
```

Keep domain modules grouped by business capability, not root-level technical layers such as
`controller`, `service`, `repository`, or `entity`.

## Runtime Profiles

See [Runtime Profiles](docs/runtime-profiles.md) for local, test, dev, qa, and prod configuration behavior.

## Linting

> Each tool covers a different layer:
>
> - **Spotless** — fixes formatting (whitespace, indentation) on demand
> - **Checkstyle** — enforces naming and style rules on every build
> - **SpotBugs** — finds runtime bugs (nulls, leaks) at bytecode level, opt-in
> - **JaCoCo** — reports test coverage during `verify`

### Formatting (auto-fix)

Use Spotless with Google Java Format to automatically fix indentation, tabs/spaces, and other style layout issues.

```bash
./mvnw spotless:apply
```

### Format on save (lightweight, VS Code + IntelliJ)

This repo keeps setup minimal:

- Shared defaults are in `.editorconfig`.
- Java formatting is standardized by Spotless + `googleJavaFormat`.
- IDE format-on-save is a convenience only; `./mvnw spotless:apply` is the source of truth.
- Git hooks live in `.githooks/` and are activated automatically on first `./mvnw` run.

### Pre-commit hook (affected files only)

The hook runs `spotless:apply` on staged `.java` files only. If spotless reformats a file the commit is aborted — `git add` the changed files and commit again.

Manual install or reinstall (macOS/Linux):

```bash
./scripts/setup-hooks.sh
```

Manual install or reinstall (Windows PowerShell):

```powershell
.\scripts\setup-hooks.ps1
```

CI should still run full-repository formatting verification (`./mvnw spotless:check`).

#### VS Code

The repo includes `.vscode/settings.json` and `.vscode/extensions.json` to keep setup light.

1. Install recommended extensions when prompted.
2. Ensure `Format on Save` is enabled (already set in workspace settings).
3. Save a Java file; it formats with Google Java Format.

#### IntelliJ IDEA

1. Install plugin `google-java-format`.
2. Enable `Preferences` -> `Tools` -> `Actions on Save` -> `Reformat code`.

If formatting ever drifts, run:

```bash
./mvnw -q spotless:apply
```

To verify formatting without changing files:

```bash
./mvnw spotless:check
```

### Checkstyle (default — runs at `validate` on every build)

Checkstyle runs automatically using [Google Java Style](https://google.github.io/styleguide/javaguide.html) via the bundled `google_checks.xml`. No external files are downloaded.

Run standalone:

```bash
./mvnw checkstyle:check
```

To suppress specific files or rules, edit `checkstyle-suppressions.xml`.

### SpotBugs (opt-in profile)

Bytecode-level static analysis. Run when you want deeper bug detection:

```bash
./mvnw verify -Pspotbugs
```

### JaCoCo coverage report

Generate a test coverage report during `verify`:

```bash
./mvnw verify
```

The HTML report is written to `target/site/jacoco/index.html`.

---

This project does **not** use Renovate or Dependabot because CI/CD has no general internet access (only access to the Maven repository).

Use Maven-native checks and update in small steps:

1. Check available updates.
2. Upgrade platform versions first (Spring Boot parent, then Spring Cloud BOM).
3. Re-run tests.
4. Apply non-platform dependency/plugin updates in small batches.

### 1) See what can be updated

```bash
./mvnw -q versions:display-dependency-updates
./mvnw -q versions:display-plugin-updates
./mvnw -q versions:display-property-updates
```

**Note:** These commands are configured to exclude **snapshots** and **milestones** (e.g., `-SNAPSHOT`, `-M1`, `-M2`) automatically via `mvn-versions-rules/rules.xml`. Only final releases will be shown as available updates.

If you want to see snapshot/milestone versions too:

```bash
./mvnw versions:set -DnewVersion=X.Y.Z -DgenerateBackupPoms=false
```

### 2) Update safely

Use targeted updates and review diffs before committing:

```bash
./mvnw versions:update-parent -DgenerateBackupPoms=false
./mvnw versions:update-properties -DgenerateBackupPoms=false
```

Then validate:

```bash
./mvnw test
```

## Optional OWASP Dependency Check Profile

An opt-in Maven profile is configured in `pom.xml`:

- Profile id: `owasp-check`
- Plugin: `org.owasp:dependency-check-maven`
- Scope: runs only when profile is explicitly enabled
- Behavior: report-focused (configured to not fail build by default)

Run it when needed:

```bash
./mvnw verify -Powasp-check
```

Generated reports are under `target/` (for example `target/dependency-check-report.html`).

## Notes

- Keep Spring-managed dependencies versionless when possible so Spring Boot/Spring Cloud BOM controls compatibility.
- For Spring Cloud AWS, verify compatibility with the current Spring Boot line before changing `spring-cloud-aws.version`.
