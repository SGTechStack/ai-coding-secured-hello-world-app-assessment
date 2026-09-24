---
name: pre-prod-check
description: "Orchestrates seven pre-production readiness gates in sequence: API code docs generation, authorization matrix verification, OWASP threat modeling, technical architecture doc (with dependency vuln scan), full-codebase IM8/ARC compliance, full-codebase observability readiness, full-codebase secrets & config audit — then writes a consolidated sign-off report for tech lead review."
argument-hint: "[project-root]"
---

# Pre-Production Readiness Check

## User Input

```text
$ARGUMENTS
```

If a project root argument is provided, use it. Otherwise, detect the project root by walking up from the current working directory to the nearest ancestor containing a dependency manifest (`package.json`, `pom.xml`, `build.gradle`, `build.gradle.kts`) or a `.git` directory. If nothing is found, fall back to the current working directory and warn the user.

---

## Workflow

### Step 0 — Initialize

1. Determine the project root.
2. Create output directory: `<project-root>/pre-prod-report/` (reuse if exists).
3. Initialize a gate results tracker:

```
gate_results = {
  "code_docs":          { status: "PENDING", findings: [], blockers: [] },
  "auth_matrix":        { status: "PENDING", findings: [], blockers: [] },
  "threat_modeling":    { status: "PENDING", findings: [], blockers: [] },
  "tech_arch_doc":      { status: "PENDING", findings: [], blockers: [] },
  "im8_compliance":     { status: "PENDING", findings: [], blockers: [] },
  "observability":      { status: "PENDING", findings: [], blockers: [] },
  "secrets_config":     { status: "PENDING", findings: [], blockers: [] },
}
```

Print: `Starting pre-production readiness check for <project-name>. Seven gates will run in sequence.`

---

### Gate 1 — API Code Documentation

1. Invoke `/gen-code-docs` using the Skill tool.
2. Extract:
   - Whether documentation was successfully generated (TSdoc/Javadoc)
   - Count of public APIs documented vs undocumented
3. Record status:
   - **PASS**: Documentation generated successfully with all public APIs and exported members documented
   - **WARN**: All public APIs documented but some internal or non-public members are missing documentation
   - **FAIL**: Public APIs are missing documentation, or documentation generation failed or could not run
4. Print gate status before proceeding.

---

### Gate 2 — Authorization Matrix

1. Invoke `/gen-auth-matrix` using the Skill tool.
2. Extract:
   - Total number of endpoints discovered
   - Number of endpoints with explicit authorization rules
   - Number of unprotected endpoints (no authorization configured)
3. Record status:
   - **PASS**: All endpoints have explicit authorization rules — zero unprotected endpoints
   - **WARN**: Has unprotected endpoints that are intentionally public (e.g., health checks, login) — requires manual confirmation
   - **FAIL**: Has unprotected endpoints that should require authorization, or the skill could not run
4. Print gate status before proceeding.

---

### Gate 3 — OWASP Threat Modeling

1. Invoke `/owasp-threat-modeling` using the Skill tool.
2. Extract:
   - Count of identified threats by status (Open, Mitigated, N/A)
3. Record status:
   - **PASS**: All identified threats are Mitigated or N/A — zero Open threats
   - **WARN**: Has Open threats at Medium or Low severity
   - **FAIL**: Has Open threats at High or Critical severity, or the skill could not run
4. Print gate status before proceeding.

---

### Gate 4 — Technical Architecture & Dependency Vulnerabilities

1. Invoke `/tech-arch-doc <project-root>/pre-prod-report` using the Skill tool — the argument is the **output folder** for the generated artefacts, not the scan target (the skill auto-detects the codebase to scan). This internally triggers `/dependency-vuln-scan`.
2. Extract:
   - Dependency count (production / dev)
   - Vulnerability summary (critical / high / medium / low counts)
3. Record status:
   - **PASS**: Zero vulnerabilities
   - **WARN**: Has medium or low vulnerabilities but no critical/high
   - **FAIL**: Has critical or high vulnerabilities
4. Print gate status before proceeding.

---

### Gate 5 — Full-Codebase IM8/ARC Compliance

**Extends `/im8-review` beyond per-feature scope to scan the entire codebase.**

1. Invoke `/im8-review` using the Skill tool. Prepend to arguments:
   > "Run a FULL CODEBASE scan — do not scope to a single feature or PR. Scan every controller, service, repository, configuration file, and frontend component in the project. The target codebase is at `<project-root>`."
2. Extract:
   - Count of FAIL findings
   - Count of WARN findings requiring manual review
3. Record status:
   - **PASS**: Zero FAIL findings
   - **WARN**: Zero FAIL but has WARN items requiring manual review
   - **FAIL**: One or more FAIL findings
4. Print gate status before proceeding.

---

### Gate 6 — Full-Codebase Observability Readiness

**Extends `/spring-logging-review` beyond per-feature scope and adds broader observability checks.**

1. Invoke `/spring-logging-review` using the Skill tool with override:
   > "Run a FULL CODEBASE scan — review every Java source file, not just changed files. The target codebase is at `<project-root>`."
2. Capture logging review findings.
3. Perform additional observability checks directly (these are not covered by `/spring-logging-review` or `/im8-review`):

#### 4a. Health & Readiness Probes (conditional)
First, detect whether the project uses container orchestration by looking for: `Dockerfile`, `docker-compose.yml`, `k8s/` directory, Helm charts (`Chart.yaml`), Kubernetes manifests (`deployment.yaml`, `service.yaml`), `skaffold.yaml`, Kustomize files.
- **If orchestration detected**: Check for readiness/liveness probe configuration (`management.endpoint.health.probes.enabled`, `management.health.livenessstate.enabled`, `management.health.readinessstate.enabled`) and custom `HealthIndicator` implementations. Missing probes is **WARN**.
- **If no orchestration detected**: Mark as **N/A** and skip.

#### 4b. Distributed Tracing (conditional)
First, detect whether the project is a multi-service architecture by looking for: multiple Spring Boot application entry points, Spring Cloud dependencies (`spring-cloud-gateway`, `spring-cloud-config`, `eureka-client`), Feign clients, `WebClient`/`RestTemplate` calls to internal services, service discovery configuration.
- **If multi-service detected**: Check for Micrometer Tracing or Spring Cloud Sleuth dependencies, trace propagation configuration (`management.tracing.*`), and span customization (`@NewSpan`, `@ContinueSpan`, `Tracer`). Missing tracing is **WARN**.
- **If single-service**: Mark as **N/A** and skip.

Note: Actuator presence, metrics (USE/RED signals, `@Timed`, `MeterRegistry`), and actuator endpoint security are already covered by Gate 5 (`/im8-review` controls `lm-16` and `as-13`). Structured logging, correlation IDs, and MDC are covered by `/spring-logging-review`. Do not duplicate those checks here.

4. Record status:
   - **PASS**: Structured logging in place, correlation IDs present, health probes configured, tracing configured
   - **WARN**: Some observability gaps (e.g., no tracing, no health probes) but logging is solid
   - **FAIL**: No structured logging, or critical logging security issues (PII leakage, log injection)
5. Print gate status before proceeding.

---

### Gate 7 — Full-Codebase Secrets & Config Audit

**Extends `/semgrep` beyond per-feature scope to scan the entire codebase.**

1. Invoke `/semgrep` using the Skill tool with override:
   > "Run a FULL CODEBASE scan — scan the entire project, not just changed files. Treat ALL findings as in-scope (there is no 'current task' — this is a pre-production audit). The target codebase is at `<project-root>`."
2. The P0/P1/P2 triage from `/semgrep` does not apply here. Reclassify all findings:
   - **Blocker**: Security vulnerabilities (injection, insecure deserialization, weak crypto), hardcoded secrets/credentials/API keys
   - **Warning**: Unsafe patterns, missing validation, dangerous dependency patterns
   - **Info**: Style-level findings, non-security suggestions
Note: Hardcoded secrets scanning, config externalization, `.gitignore` validation, and Spring Security config checks (`SecurityFilterChain`, `permitAll()`, actuator security) are already covered by Gate 5 (`/im8-review` controls `as-7`, `as-8`, `as-13`). Do not duplicate those checks here.

3. Record status:
   - **PASS**: Zero blockers from Semgrep
   - **WARN**: No blockers but has warnings
   - **FAIL**: Critical Semgrep findings
5. Print gate status before proceeding.

---

### Report — Write Consolidated Sign-Off Report

Assemble all gate results into `<project-root>/pre-prod-report/readiness-report-<YYYYMMDD-HHmmss>.md` (timestamped for versioning):

```markdown
# Pre-Production Readiness Report

> Project: <project name>
> Generated: <ISO 8601 timestamp>
> Audited by: Claude Code (automated)

## Executive Summary

| Gate | Status | Blockers | Warnings |
|------|--------|----------|----------|
| 1. API Code Documentation | PASS/WARN/FAIL | N | N |
| 2. Authorization Matrix | PASS/WARN/FAIL | N | N |
| 3. Threat Modeling | PASS/WARN/FAIL | N | N |
| 4. Tech Architecture & Vuln Scan | PASS/WARN/FAIL | N | N |
| 5. IM8/ARC Compliance | PASS/WARN/FAIL | N | N |
| 6. Observability Readiness | PASS/WARN/FAIL | N | N |
| 7. Secrets & Config Audit | PASS/WARN/FAIL | N | N |

**Overall Verdict: READY / NOT READY / CONDITIONALLY READY**

- **READY**: All gates PASS
- **CONDITIONALLY READY**: No FAIL gates, but WARN gates exist requiring manual review
- **NOT READY**: One or more gates FAIL

## Blockers (must resolve before production)

<numbered list of all blockers across all gates, grouped by gate, with file paths and line numbers>

## Warnings (manual review recommended)

<numbered list of all warnings across all gates, grouped by gate>

## Gate 1: API Code Documentation
### Status: <PASS/WARN/FAIL>
<documentation generation summary, count of documented vs undocumented public APIs>

## Gate 2: Authorization Matrix
### Status: <PASS/WARN/FAIL>
<total endpoints, protected vs unprotected, list of unprotected endpoints if any>

## Gate 3: Threat Modeling
### Status: <PASS/WARN/FAIL>
<summary and open threats if any>

## Gate 4: Technical Architecture & Dependency Vulnerabilities
### Status: <PASS/WARN/FAIL>
<dependency summary, vulnerability findings table, link to full tech-architecture.md>

## Gate 5: IM8/ARC Compliance
### Status: <PASS/WARN/FAIL>
<compliance summary table — control ID, status, finding>
<details on FAIL and WARN items>

## Gate 6: Observability Readiness
### Status: <PASS/WARN/FAIL>
### Logging
<logging review summary>
### Health & Readiness Probes
<probe configuration findings>
### Distributed Tracing
<tracing configuration findings>

## Gate 7: Secrets & Config Audit
### Status: <PASS/WARN/FAIL>
### Semgrep Findings
<findings summary>

```

Print the executive summary and overall verdict to the conversation.

---

## Rules

- **Sequential execution** — gates run in order (1 through 7). Do not skip or parallelize.
- **Do not block on gate failure** — record the failure and continue. All gates must run so the report is complete.
- **Full codebase scope** — all gates scan the entire codebase, not just changed files or a single feature.
- **Read-only** — do not modify project source files. Only write to `pre-prod-report/`.
- **Skill delegation** — use the Skill tool to invoke sub-skills. Do not reimplement their logic.
- **Graceful degradation** — if a skill is unavailable or fails, record what was skipped and continue.
- **No false passes** — if evidence is ambiguous or a check cannot be completed, mark WARN with a manual review flag, never PASS.
