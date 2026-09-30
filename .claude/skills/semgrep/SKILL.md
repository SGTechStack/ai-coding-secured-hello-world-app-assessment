---
name: semgrep
description: Run Semgrep static analysis against changed code to catch security vulnerabilities, hardcoded secrets, injection flaws, insecure deserialization, and unsafe patterns. Use as part of the /code-reviewer feedback loop before committing, scoped to code changed or created by the current task.
---

# Semgrep Static Analysis

Run Semgrep against the codebase to surface security findings before commit.

## Prerequisites

Semgrep CLI must be installed:

```bash
pip install semgrep
# or
brew install semgrep
```

## Quick start

| Invocation | Behaviour |
|------------|-----------|
| `/semgrep` | Scan full codebase, triage findings by task scope, report |
| `/semgrep --ruleset=auto` | Use Semgrep's auto ruleset if project has `.semgrep.yml` |

## Workflow

### 1. Run the scan

Run against the entire codebase -- findings are triaged by task scope in the next step:

```bash
semgrep --config p/security-audit --config p/default .
```

Use `--config auto` if the project has a `.semgrep.yml` config.

### 2. Triage findings

*   **P0** -- Issues in code created or modified by this task -- MUST fix before committing
*   **P1** -- Issues in pre-existing code made worse by this task (e.g., introducing a vulnerable pattern into shared code) -- MUST fix before committing
*   **P2** -- Pre-existing issues unrelated to this task -- Document but do NOT fix in this workflow

### 3. Fix P0 and P1 findings

Fix in this priority order:

1. Security vulnerabilities (injection flaws, insecure deserialization, weak crypto)
2. Hardcoded secrets, credentials, or API keys
3. Unsafe input validation or output encoding gaps
4. Dangerous dependency patterns or known CVE patterns

Add `# nosemgrep` annotations only for confirmed false positives, with a comment explaining why.

### 4. Re-run and repeat

Re-run the test suite after fixes, then re-run Semgrep. Repeat until no P0 or P1 findings remain.

## Rules

- Scope fixes to code touched by the current task -- do not fix P2 findings in unrelated code
- `# nosemgrep` suppressions require a justification comment
- PASS means zero P0 and P1 findings -- P2s are documented but do not block
