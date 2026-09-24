---
name: build-check
description: Run automated build checks for changed TypeScript/React and Java/Maven code, covering formatting, type-checking or compilation, tests, coverage thresholds, production builds, and codebase audits. Use when the code-reviewer dispatcher runs its build check.
---

# Build Check

## Instructions

Detect each technology present in the changeset and run its checks:

**TypeScript/React**

- [ ] Format: `prettier --check .`
- [ ] Type-check: `tsc --noEmit`; run `typegen` first if using React Router v7
- [ ] Test + coverage: `npx vitest run --coverage`; all thresholds in `vitest.config.ts` must be >=80%
- [ ] Build: `vite build`
- [ ] Codebase audit: `npx fallow audit`

**Java/Maven**

- [ ] Compile: `mvn compile`
- [ ] Verify: `mvn verify`; runs unit tests, integration tests, merges JaCoCo coverage, and enforces >=80% instruction coverage. Report: `target/site/jacoco/index.html`

## Return

Status: PASS | FAIL
Failed checks: <commands and concise output or none>
