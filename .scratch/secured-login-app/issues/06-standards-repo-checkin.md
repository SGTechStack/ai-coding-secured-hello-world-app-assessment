# 06 — How `App-Standards/` is tracked

Type: task
Status: open
Blocked by: —
Map: [Secured Login App](../map.md)

## Question

`App-Standards/` sits inside the working tree as a **nested git repository** (it has its own `.git/`), is untracked by this repo, and holds roughly 17,000 lines of standards. It is currently invisible to `git status` beyond the bare directory entry.

Decide and then carry out how it is tracked, before anything on branch `taniakoh` is committed:

- **Committed as plain files** — the standards travel with the branch, so a reviewer sees exactly what the build was held to. Costs a large diff and duplicates a repo that has its own upstream.
- **Git submodule** — records the upstream and the pinned commit without copying content. Requires the upstream URL to be reachable by reviewers.
- **Gitignored** — the branch stays clean, but the binding standards are then absent from the artefact, and the map's citations point at nothing a reviewer can open.

Check `App-Standards/.git/config` for the upstream remote before deciding, and check whether other participants' branches already made this call (the remote has ~12 sibling branches).

This is a `task`, not a decision about the app — but it blocks the first commit of the map itself, which is why it sits on the frontier. Record what was done and the resulting facts (upstream URL, pinned commit, or the ignore rule added).
