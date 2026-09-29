# 20 — Agent tooling: committed or ignored

Type: task
Status: open
Blocked by: —
Map: [Secured Login App](../map.md)

## Question

The repo has **no `.gitignore` at all**, and four untracked paths remain after [06 — How `App-Standards/` is tracked](06-standards-repo-checkin.md) settled the standards:

- `.agents/skills/` — includes `wayfinder` itself, the skill that produced this map.
- `agent/skills/` — a second, separately-rooted skill tree.
- `.claude/` — Claude Code local config.
- `skills-lock.json` — the skill install lockfile.

Decide which are committed and which are ignored, and write the resulting `.gitignore`. Same shape as 06 and it blocks the same first commit, but the answer is not the same: these are *method*, and the map is already a committed artefact (`.scratch/` is tracked on this branch), so there is a real argument that the skills which generated it travel with it.

Two sibling precedents, both ignoring all of it: `mingliang` ignores `.claude/`, `.agents/`, `.kiro/`; `samuelwong` ignores those plus `skills-lock.json`, `.scratch/` and `artifacts/` — note `.scratch/`, which this branch deliberately tracks. Neither mentions `agent/`. Read both with `git cat-file -p <blob>` from the sibling branches rather than re-deriving.

Watch for secrets and machine-local state before committing anything under `.claude/` (per the working rule on local-only files), and check whether the two skill trees are duplicates of each other.
