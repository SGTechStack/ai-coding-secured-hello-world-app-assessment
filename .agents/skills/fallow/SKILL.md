---
name: fallow
description: Use when finishing a frontend feature, fixing a bug, before committing TypeScript/React code, or when the user asks to clean up dead code, duplication, circular dependencies, or complexity hotspots. Scans the frontend/ TypeScript codebase with fallow and returns a pass/warn/fail verdict.
version: "1.0.0"
---

# Fallow

Codebase intelligence for the **frontend** TypeScript/React code: unused (dead) code, code duplication, circular dependencies, complexity hotspots, and architecture boundary violations. No type-checking, no AI inside the analyzer — fast and deterministic.

Fallow only applies to `frontend/` (the only TypeScript folder). It is installed as a `frontend` devDependency, so always run it from `frontend/` via `npx fallow`.

This skill complements `react-doctor`: react-doctor covers React-specific security / accessibility / bundle concerns; fallow covers dead code, duplication, circular deps, and complexity.

## Default — before committing / after a change (working-tree gate)

Scope the audit to **your uncommitted changes only**. This is the everyday guardrail and the default — it avoids touching files outside your current work, which prevents wide refactors and the merge conflicts they cause.

```bash
cd frontend
git diff HEAD -- 'src/**/*.ts' 'src/**/*.tsx' | npx fallow audit --diff-stdin --format compact
```

`audit` returns a verdict (pass / warn / fail). By default (`--gate new-only`) only findings **introduced by your changes** affect the verdict; pre-existing ("inherited") issues are reported but do not fail the gate. Fix the introduced findings before committing; leave inherited ones alone unless explicitly asked to clean up.

If the working tree is clean, `--diff-stdin` parses 0 lines and there is nothing to gate — that is expected.

## Opt-in — audit the whole PR (branch gate)

When you want the full picture of everything in the PR (not just uncommitted edits), scope against the gitflow **integration branch**. Do NOT rely on `audit`'s auto-detect here — it resolves to `main`, which under gitflow pulls in every commit since the last release. Resolve the integration branch explicitly (prefer `develop`, fall back to `develop-uc`):

```bash
cd frontend
BASE=$(git rev-parse --verify --quiet develop >/dev/null && echo develop || echo develop-uc)
npx fallow audit --changed-since "$BASE" --format compact
```

Override `BASE` when the actual PR target differs.

## Opt-in — full-codebase cleanup (explicit request only)

Only run these when the user explicitly asks to clean up or improve quality. They scan the entire `frontend/` codebase, so acting on them produces wide diffs — never a reflex during normal PR work.

```bash
cd frontend
npx fallow health --score --hotspots --targets   # complexity / maintainability / refactor targets
npx fallow dead-code                              # unused code + dependency hygiene + cycles
npx fallow dupes                                  # copy-paste and structural duplication
```

Fix issues by severity, errors first.

## Notes

- Add `--format json` to any command for machine-readable output.
- Fallow caches in `frontend/.fallow/` (gitignored).
- Run `npx fallow <command> --help` for the full flag set.
