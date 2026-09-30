# Report Template

Default path: `artifacts/browser-test/{feature}-browsertest.md`

```markdown
# Browser Acceptance Test Report

**Date:** YYYY-MM-DD HH:MM
**Issue:** #<number> - <title>
**Base URL:** <resolved base URL>
**Run Command:** <docker compose or documented app command>
**Scope:** <all | AC1, AC2, ...>

## Results Summary

| Criterion | Passed | Failed | Skipped | Blocked |
|-----------|--------|--------|---------|---------|
| AC1 - <title> | 1 | 0 | 0 | 0 |
| AC2 - <title> | 0 | 1 | 0 | 0 |

## Detailed Findings

### AC1 - <criterion title>
**Status:** PASS
**Evidence:** Snapshot showed <observable text / control / state>.

### AC2 - <criterion title>
**Status:** FAIL
**Evidence:** Expected <specified behaviour>, but snapshot showed <actual behaviour>.
**Recommendation:** Implement or correct the missing behaviour described by the criterion.

### AC3 - <criterion title>
**Status:** SKIP
**Reason:** Required test data or precondition was not available: <details>.

### AC4 - <criterion title>
**Status:** BLOCKED
**Reason:** <crash / auth wall / unrecoverable state>: <details>.

## Screenshots

- `<path-to-screenshot>`

## Sign-off

**Result:** PASS / FAIL / PARTIAL / BLOCKED
**Blockers:** <list any blocking issues preventing full sign-off, or "None">
```
