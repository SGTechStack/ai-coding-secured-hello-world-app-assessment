# Report Template

Default path: `artifacts/mutation-testing/{feature}-mutation.md`

```markdown
# Mutation Testing Report

**Date:** YYYY-MM-DD
**Ecosystem:** <detected ecosystem>
**Tool:** <pit|stryker|mutmut|cosmic-ray|dotnet-stryker|custom>
**Tool Config:** <path or none>
**Baseline Command:** `<command>` - PASS / FAIL / WARN
**Mutation Command:** `<command>`
**Target Scope:** <scope>
**Target:** <target or auto>
**Mutation Threshold:** X%

## Summary

- Mutation score: **X%** - **PASS / WARN / FAIL**
- Total: N | Killed: N | Survived: N | No coverage: N | Timeout: N | Skipped: N

## Scores by Target

| Target | Mutants | Killed | Survived | No Coverage | Timeout | Score |
|--------|---------|--------|----------|-------------|---------|-------|
| ...    | ...     | ...    | ...      | ...         | ...     | ...   |

## Findings

### [F01] Missing Test / No Coverage

**Target:** `<file/module/class/function>`
**Mutation:** `<tool mutation description>`
**Severity:** High
**Evidence:** `<report excerpt or tool output reference>`
**Recommendation:** Add a test covering the missing behaviour.

### [F02] Weak Assertion / Survived Mutant

**Target:** `<file/module/class/function>`
**Mutation:** `<tool mutation description>`
**Severity:** Medium
**Evidence:** `<report excerpt or tool output reference>`
**Recommendation:** Strengthen assertions around the changed behaviour.

### [F05] Equivalent Mutation Suspected

**Target:** `<file/module/class/function>`
**Mutation:** `<tool mutation description>`
**Severity:** Low
**Evidence:** Manual review - `<reason>`.
**Recommendation:** Verify equivalence. If confirmed, exclude narrowly using the tool's supported config.

## Remediation Priority

1. [Critical] ...
2. [High] ...
3. [Medium] ...
4. [Low] ...

## Artifacts

- Machine-readable report: `<path if available>`
- HTML/text report: `<path if available>`
- Tool logs: `<path or captured command output summary>`

## History

- Previous score: X% / unavailable
- Current score: X%
- Delta: +N / -N / unchanged
```

## Trend Comparison (`--history`)

When `--history` is set:

1. Use the mutation tool's native history feature if configured.
2. Otherwise compare against the latest prior `mutation-*.md` report in the output directory.
3. Report score delta, new survived/no-coverage findings, and resolved findings.

Trend status:
- **Improved** - overall score increased or high-severity survivors decreased.
- **Worsened** - score dropped or new high-severity survivors appeared.
- **Stable** - no meaningful change.
- **Unavailable** - no prior comparable run.
