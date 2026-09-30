# .gitignore Check

Use immediately before staging and committing.

## Detection and Update Logic

1. Detect every stack and tool from manifests and configuration. Use `git status --short --untracked-files=normal`; treat untracked directories as atomic and do not enumerate dependency, build, cache, or coverage directories.
2. Preserve an existing `.gitignore` or create one. Classify each untracked path, then add the narrowest useful rules for local-only, reproducible, tool-generated, IDE, cache, temporary, or secret-bearing paths. Use the patterns below as examples, not an exhaustive list.
3. Treat agent-runtime skill installations and their lock files as local tooling (for example, `.claude/`, `.agents/`, `.kiro/`, `agent/skills/`, and `skills-lock.json`) unless repository policy explicitly identifies them as project deliverables. Do not infer that they are project deliverables merely because `AGENTS.md` uses or references them.
4. Do not ignore workflow evidence required for review, closeout, or audit, even when generated. Resolve its current locations from the workflow instructions. Preserve other intentional project artifacts.

## Patterns by Technology

- **Shared:** `*.log`, `.env`, `.env.*`, `!.env.example`, `.DS_Store`, `Thumbs.db`, `*.tmp`, `*.swp`
- **Node.js/React:** `node_modules/`, `dist/`, `build/`, `coverage/`, `.vite/`, `*.tsbuildinfo`
- **Java/Maven:** `target/`, `*.class`

## Verification Gate

Review `.gitignore`, verify every added rule against a representative generated path with `git check-ignore -v`, and review `git status --short --untracked-files=normal`.

Complete when every relevant generated path is ignored, intentional project artifacts remain visible, and existing rules are preserved.
