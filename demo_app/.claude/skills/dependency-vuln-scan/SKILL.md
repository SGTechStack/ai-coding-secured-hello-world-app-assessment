---
name: dependency-vuln-scan
description: "Scans project dependencies for known CVEs using the appropriate scanner for the detected stack. Produces a point-in-time vulnerability report."
argument-hint: "[project-root]"
---

# Dependency Vulnerability Scan

## User Input

```text
$ARGUMENTS
```

The argument is the **project root** to scan. If omitted, walk up from the cwd to the nearest dependency manifest (`package.json`, `pom.xml`, `build.gradle(.kts)`) or `.git` directory. Fall back to cwd.

---

## What This Skill Does

Detect the project's tech stack from its manifest files, run the appropriate vulnerability scanner, and produce a Markdown report of findings sorted by severity.

## Stack Detection & Scanners

| Manifest | Scanner |
|----------|---------|
| `package.json` + `package-lock.json` | `npm audit --json` |
| `package.json` + `yarn.lock` | `yarn audit --json` |
| `pom.xml` | `mvn org.owasp:dependency-check-maven:check -DfailBuildOnCVSS=0 -Dformat=JSON` |
| `build.gradle(.kts)` | `./gradlew dependencyCheckAnalyze` (requires OWASP plugin in build file) |

## Procedure

1. **Discover all manifests** -- check for `package.json`, `package-lock.json`, `yarn.lock`, `pom.xml`, `build.gradle`, and `build.gradle.kts` in the project root and one level of subdirectories (e.g. `frontend/`, `backend/`).
2. **Build a scan plan** -- map every manifest found to its scanner from the table above. List the plan before executing.
3. **Update NVD data (OWASP dependency-check only)** -- **skip this step entirely if the scan plan only contains npm/yarn scanners.** If the scan plan includes Maven or Gradle OWASP checks, you **MUST** run the dedicated NVD update command **before** running the scan in Step 4.
   - Check for the local NVD data directory (typically `~/.m2/repository/org/owasp/dependency-check-data/` or `build/` for Gradle). If the data exists and was last updated within 24 hours, skip the update.
   - If the data is missing or stale, run `mvn org.owasp:dependency-check-maven:update-only` (or the Gradle equivalent). Inform the user this may take a while on first run.
   - If the update fails or times out, note it in **Scanner notes** and continue -- the scan may still work with stale data, or it will fail gracefully in the next step.
4. **Run every applicable scanner** -- do not stop after the first match. If both `package.json` and `pom.xml` exist, run both the npm/yarn audit **and** the Maven OWASP check. Tag each finding with its ecosystem/scanner source and merge into one report.

## Scanner-Specific Notes

- **Verify scanner availability** before running. If missing, skip with a note -- never install scanners.
- **npm audit** returns non-zero when vulnerabilities exist; this is expected, not an error. If `package-lock.json` is missing, generate it with `npm install --package-lock-only` first.
- **Maven OWASP plugin** can be invoked as a fully-qualified goal without being declared in `pom.xml`. Parse results from `target/dependency-check-report.json`. If the plugin fails, fall back to `mvn dependency:tree` and note that only dependency listing was possible.
- **Gradle OWASP plugin** must already be configured in the build file. If absent, skip -- do not modify build files. Parse from `build/reports/dependency-check-report.json`.

## Report Format

For each vulnerability, extract: **ID** (CVE/GHSA), **severity**, **package name**, **installed version**, **fixed version** (if known), and a **short description**.

Output a Markdown report with:
1. **Header** -- scan timestamp (ISO 8601), project name, scanner(s) used
2. **Summary table** -- count by severity (critical/high/medium/low/info)
3. **Findings** -- one section per severity level (omit empty levels), tabular rows sorted by package name
4. **Remediation guidance** -- for critical and high findings, suggest the upgrade command
5. **Scanner notes** -- any warnings (missing scanners, partial results, timeouts, network issues)

If no vulnerabilities are found, output just the header and "No known vulnerabilities found."

## Rules

- **Read-only** -- never modify source files, build files, or manifests (exception: generating a missing `package-lock.json` with `--package-lock-only`)
- **Fail gracefully** -- partial results with clear notes are better than no output. If any step fails (scanner not found, command error, timeout, network issue, parse failure), document the failure in the **Scanner notes** section with the step name, the error message, and the exit code if available
- **Point-in-time** -- results reflect current state; re-run to refresh
