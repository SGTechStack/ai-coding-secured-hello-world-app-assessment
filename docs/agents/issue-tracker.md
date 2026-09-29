# Issue tracker: GitHub

Issues for this repo are GitHub issues. Use `gh` from a clone; it infers the remote.

The implementation slices for this assessment are written in `docs/spec/implementation-plan.md`. Publish a GitHub issue only when a skill asks to publish to the tracker.

## Conventions

- **Create:** `gh issue create --title "..." --body "..."`. Pass a multi-line body with a heredoc.
- **Read:** `gh issue view <number> --comments`.
- **List:** `gh issue list --state open --json number,title,body,labels`.
- **Comment:** `gh issue comment <number> --body "..."`.
- **Labels:** `gh issue edit <number> --add-label "..."` and `--remove-label "..."`.
- **Close:** `gh issue close <number> --comment "..."`.

Label names are the ones in `triage-labels.md`.

## Pull requests

External pull requests are not a feature-request inbox. Triage issues, not drive-by PRs.

If a skill has to open a PR, use `gh pr create` and keep the work on the author's branch. Do not open a PR against someone else's branch, and do not commit to `main`.

## When a skill says "publish to the issue tracker"

Create a GitHub issue with the matching triage label.

## When a skill says "fetch the relevant ticket"

Run `gh issue view <number> --comments`. If the number is only in the implementation plan, read that slice instead of inventing an issue.

## Wayfinding

A map is one issue labeled `wayfinder:map`. Child tickets are issues that start with `Part of #<map>` and use `wayfinder:research`, `wayfinder:prototype`, `wayfinder:grilling`, or `wayfinder:task`.

A blocked ticket says `Blocked by: #<n>` at the top. It is ready when those issues are closed and it has no assignee. Claim it with `gh issue edit <n> --add-assignee @me`. Close it with a comment that states the outcome, then add a line to the map.
