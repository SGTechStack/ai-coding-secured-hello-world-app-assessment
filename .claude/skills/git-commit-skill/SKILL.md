---
name: git-commit-skill
description: Create well-formed git commits following Conventional Commits, splitting changes into logically separated commits (one logical change each). Use when the user asks to commit, "make a commit", "commit this", "split into commits", or wants help writing commit messages.
---

# git-commit

## Core principle: one logical change per commit

A commit should be a single, self-contained, reviewable change. Before committing, inspect the diff and **group changes by intent**, not by file. Split unrelated work into separate commits even when it sits in the same file.

Separate commits for:

- Different features or fixes (each gets its own commit)
- Functional change vs. pure formatting/style reflow
- Functional change vs. version bumps / dependency updates
- Refactors vs. behavior changes
- Generated/regenerated artifacts vs. hand-written source

## Workflow

1. **Survey** — `git status` and `git diff` to see everything pending.
2. **Group** — list the distinct logical changes. State the plan to the user when more than one commit is needed.
3. **Stage one group** — stage only the files/hunks for that change. Use `git add <path>`, or `git add -p` for partial files; `git restore --staged` to undo.
4. **Commit** — write the message (format below). Repeat 3–4 for each remaining group.
5. **Verify** — `git log --oneline -n <count>`.

## Message format (Conventional Commits)

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

- Allowed **types** only: `build`, `chore`, `ci`, `docs`, `feat`, `fix`, `perf`, `refactor`, `revert`, `style`, `test`
- Add **scope** when it clarifies the area: `feat(lang): add Polish language`
- Add `!` after the type/scope **only for breaking changes**: `feat(api)!: change profile response format`
- **Length limit**: the entire header (type + scope + description) must not exceed 72 characters (`header-max-length`); keep the subject under 50 where possible (the 50/72 rule).
- **Capitalization**: the description must start with a capital letter (`subject-case`, sentence case).
- **No trailing punctuation**: no period at the end of the description line (`subject-full-stop`).
- Description states the **user-visible or architectural change**, not implementation steps.
- Body (optional) explains _why_ and notes any skipped checks.

## Rules

- **Do NOT add `Co-Authored-By` trailers.**
- **Secrets and credentials**: never commit API keys, passwords, tokens, or sensitive `.env` files. Ensure they are listed in `.gitignore`.
- Do not commit generated files, local logs, IDE state, or build output unless the repo tracks them.
- **Don't duplicate format/lint/test.** If the current task already ran them, or a pre-commit hook will, skip them. Run them yourself **only** if no one else has. Note any skipped checks in the body or PR.
  - Use whatever the project defines (check its docs / config). Examples: `./mvnw spotless:apply` (Java/Maven), `npm run lint` / `npm run format` (Node), `ruff` / `black` (Python).
- Interactive flags (`git rebase -i`, `git add -i`) are unsupported here.

## Example: splitting a mixed working tree

Diff contains: a new validation feature in `service.ts`, plus Prettier reflow in `utils.ts`.

```bash
git add src/service.ts
git commit -m "feat(checkout): validate coupon codes before applying"

git add src/utils.ts
git commit -m "style: reformat utils with prettier"
```