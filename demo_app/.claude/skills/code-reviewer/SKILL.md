---
name: code-reviewer
description: Review dispatcher that runs registered quality and compliance checks against changes in parallel using subagents and writes a consolidated HTML report to artifacts/code-reviewer/{slug}-compliance.html. Use when running quality/compliance gates before commit, when invoked from the /do-work Step 2 validation gate, or when the user asks for im8, semgrep, react, springboot, or code-review audits.
---

# Code Review Dispatcher

## Quick start

| Invocation | Behaviour |
|------------|-----------|
| `/code-reviewer` | Run every registered check concurrently and overwrite the current report |
| `/code-reviewer <check>` | Run only the specified check and update its section in the report |

## Workflows

### Running Code Review

1. **Resolve Report Slug**: When called by `/do-work`, derive `<issue-number>-<issue-slug>` from the selected issue. Otherwise use `git branch --show-current`, falling back to `unnamed`.
2. **Identify Active Checks**:
   - By default, run all registered checks.
   - If a specific check is requested (e.g., `/code-reviewer im8`), only run that check.
   - Skip any check if its review skill is not registered in the system.
3. **Spawn Review Subagents**:
   - For each active check, spawn a child subagent of type `self` or a custom subagent using `invoke_subagent`.
   - Run them concurrently in a shared workspace (`Workspace: share` or `inherit`).
   - Create a fresh system-temp directory for each run and assign each check one unique artifact path inside it.
   - Prompt each subagent to run its specific review skill and write to its assigned artifact path (e.g., `"Run the /im8-review skill against the current changes and output the findings to <assigned-artifact-path>, ONLY give the file location to the main agent nothing else"`).
   - Do NOT read any of these files yourself


4. **Spawn Aggregator Subagent**
   - Wait for all subagents to complete.
   - Spawn an aggregator subagent that does step 5, 6, 7
   - Prompt it to start by reading and following Steps 5-7 of this `SKILL.md`, then give it the run's temp directory, active checks, returned artifact paths, and resolved report slug. Fail if there is not exactly one unique, readable artifact inside that directory for every active check.

5. **Collect and Aggregate Results**:
   - If a check reports `FAIL`, assign `FAIL`; otherwise use the severity mapping below.
   - For each check, read the child findings and assign a status:
     - `PASS`: Zero Critical or High findings.
     - `WARN`: Medium or Low findings only.
     - `FAIL`: At least one Critical or High finding.
   - The overall status is `FAIL` if any single check is `FAIL`.
6. **Write Compliance Report**:
   - Write the consolidated findings to `artifacts/code-reviewer/{slug}-compliance.html`.
   - Follow the layout, scaffold, and Tailwind class conventions in [HTML-REPORT.md](HTML-REPORT.md).
7. **Return Verdict**: Output the final overall status (`PASS` or `FAIL`) to the calling workflow. Also return only the compliance report path; do not return findings. Do not attempt to fix code; fixes must be performed by the calling workflow (e.g., `/do-work`).

## Registered Checks

| Check | Skill(s) invoked | Covers |
|-------|------------------|--------|
| `build` | `/build-check` | Formatting, type-checking, compilation, tests, coverage, builds, and codebase audits |
| `thermo-nuclear` | `/thermo-nuclear-review` | Code smells, dead code, duplication, naming, complexity, maintainability |
| `code-review` | `/code-review` | Two-axis review: Standards (repo conventions) + Spec (originating issue) |
| `semgrep` | `/semgrep` | Semgrep static analysis — injection flaws, hardcoded secrets, unsafe input handling, weak crypto, dangerous dependency patterns |
| `im8` | `/im8-review` | IM8 Application Technical Controls — input validation, auth, secrets, CSP, session management, audit logging, cryptography |
| `react` | `/react-review` | React 19 + TanStack standards — component structure, hooks, routing, query/form patterns, TypeScript |
| `springboot` | `/spring-core-review` + `/spring-web-review` + `/spring-data-review` + `/spring-batch-review` + `/spring-security-review` + `/spring-logging-review` + `/spring-test-review` | Spring Boot / Java — package structure, JPA, REST conventions, exception handling, security config, profile-gated dev safety, logging and telemetry best practices, and test-suite quality. Run all seven and combine findings. |
