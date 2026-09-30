# Tool Adapters

### PIT / JVM

Prefer Maven or Gradle wrappers when present. Apply `--target`, `--threshold`, `--history`, and `--timeout` through tool-supported CLI properties only. For Gradle, require an existing PIT plugin/config. Kotlin projects may need a Kotlin PIT plugin already configured.

```bash
# Maven
mvn org.pitest:pitest-maven:mutationCoverage

# Gradle
./gradlew pitest
```

### StrykerJS / JavaScript and TypeScript

Prefer existing `stryker.conf.*` or `stryker.config.*` and package scripts. Do not generate a new Stryker config during this command. Respect configured test runner, transpiler, and coverage analysis settings.

```bash
npx stryker run
```

### mutmut / Python

Use project configuration from `pyproject.toml`, `setup.cfg`, or `mutmut_config`. Prefer configured paths and test command. Be cautious with dynamic imports and integration-heavy tests.

```bash
mutmut run
```

### Cosmic Ray / Python

Use existing `cosmic-ray.toml` or documented project command.

```bash
cosmic-ray run cosmic-ray.toml
```

### Stryker.NET / .NET

Prefer local dotnet tool configuration and solution/project settings already in the repository.

```bash
dotnet stryker
```

### Custom

If `--command=<mutation-command>` is provided, run it exactly after baseline tests pass. Capture stdout/stderr, exit code, report files, and machine-readable output. Clearly document any parsing limitations in the report.
