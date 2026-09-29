# 06 — How `App-Standards/` is tracked

Type: task
Status: resolved
Assignee: taniakoh
Blocked by: —
Map: [Secured Login App](../map.md)
Validated: approved — [validation record](../handoff/validation.md)

## Question

`App-Standards/` sits inside the working tree as a **nested git repository** (it has its own `.git/`), is untracked by this repo, and holds roughly 17,000 lines of standards. It is currently invisible to `git status` beyond the bare directory entry.

Decide and then carry out how it is tracked, before anything on branch `taniakoh` is committed:

- **Committed as plain files** — the standards travel with the branch, so a reviewer sees exactly what the build was held to. Costs a large diff and duplicates a repo that has its own upstream.
- **Git submodule** — records the upstream and the pinned commit without copying content. Requires the upstream URL to be reachable by reviewers.
- **Gitignored** — the branch stays clean, but the binding standards are then absent from the artefact, and the map's citations point at nothing a reviewer can open.

Check `App-Standards/.git/config` for the upstream remote before deciding, and check whether other participants' branches already made this call (the remote has ~12 sibling branches).

This is a `task`, not a decision about the app — but it blocks the first commit of the map itself, which is why it sits on the frontier. Record what was done and the resulting facts (upstream URL, pinned commit, or the ignore rule added).

## Answer

**`App-Standards/` is tracked as a proper git submodule**, pinned at upstream `main` commit `ff5ab8205fdfb641210164eeaa2365e48e04846b` ("Delete Appfw-Project-Bootstrap/Mcc directory", 2026-09-10). Done, staged, not yet committed:

- `.gitmodules` created — one entry, `path = App-Standards`, `url = https://github.com/SGTechStack/App-Standards` (the upstream already configured in `App-Standards/.git/config`).
- `App-Standards` staged as a gitlink: `git ls-files --stage App-Standards` → `160000 ff5ab820… 0 App-Standards`.
- The nested repository is untouched: still on `main`, worktree clean, so `git -C App-Standards pull` still works and the standards can be re-pinned later by staging a new gitlink.

### What decided it: the two repos have opposite visibility

The deciding fact is **not** in the three options as the question framed them. Unauthenticated `curl`:

- `https://github.com/SGTechStack/ai-coding-secured-hello-world-app-assessment` → **200**. This assessment repo is **public**.
- `https://github.com/SGTechStack/App-Standards` → **404**. The standards repo is **private**.

So **"committed as plain files" is not a large-diff tradeoff, it is a disclosure**: vendoring ~17,000 lines of a private internal org repo into a public one publishes it, and the copy would carry the upstream's own tooling too (`.DS_Store`, `.agent/skills/**`, `.claude/**` — 236 tracked files). That rules the option out on a ground the ticket did not anticipate, and no line count would have.

A gitlink leaks nothing: a public reader sees a directory pointing at a commit in a repo they cannot open, and a reviewer with SGTechStack access gets the exact bytes with `git submodule update --init`. Gitignoring loses both the pinned commit and the citations, for reviewers who *do* have access — strictly worse than the submodule at no benefit.

### Sibling branches: two of eleven had already made this call, and made it half-way

Checked all eleven participant branches (`git ls-tree -r <branch> | grep App-Standards`):

- **`mingliang`** and **`copilot/perform-security-assessment`**: `160000 commit ff5ab820… App-Standards` — the *same* gitlink, at the *same* commit as ours, but **neither branch has a `.gitmodules`** (confirmed by `git ls-tree <branch>`, top level). That is the accidental-submodule state: `git add` swept up a directory containing `.git/`, and the result clones as an empty directory that `git submodule update --init` cannot fill, because nothing records the URL. Our decision is their choice *finished*, not a departure from it.
- **`samuelwong`**: `040000 tree … App-Standards` — vendored as plain files, 208 of them (ours pins 236, so their copy predates or prunes the current upstream). This is the disclosure case above, already pushed.
- The other eight (`alexang`, `aweeyial`, `chris`, `derick`, `jiahao`, `junkiat`, `mingliang` aside, `victorcheong`, `zacharylim`) and `main`: no `App-Standards` at any path. Eight of eleven ignored it, which is why the map's citations resolve on no branch but ours.

### Resulting facts the rest of the map depends on

- **Upstream URL:** `https://github.com/SGTechStack/App-Standards` (private, SGTechStack org).
- **Pinned commit:** `ff5ab8205fdfb641210164eeaa2365e48e04846b`, upstream `main` tip at pinning time (`git ls-remote --heads origin` in the nested repo).
- **Every map citation is pinned to that commit**, and citations are `path:line` — so they are only stable while the gitlink is. Verified against the pin, not just the worktree: `Standalone_User_Access_Control_Application_Standard.md:407` and `:415` are the two `[Enforced Constraint]` lines 03 leaned on for the `roles` table. A re-pin invalidates line numbers map-wide; treat bumping the submodule as a map-level event, not a routine update.
- **Reviewers need one extra step:** `git clone --recurse-submodules`, or `git submodule update --init` after cloning. Recorded in the map's Notes so every session states it; wiring it into the handoff README is [17 — Handoff to delivery pipeline](17-handoff-to-delivery-pipeline.md)'s.
- **No `.gitignore` rule was added**, and the repo still has none. The remaining untracked agent tooling (`.agents/`, `agent/`, `.claude/`, `skills-lock.json`) is the *same* first-commit question with a different answer, and is now [20 — Agent tooling: committed or ignored](20-agent-tooling-tracking.md).

### Not committed

Staged only. Per the working rule on state management, the commit is the user's to make: `.gitmodules` + the `App-Standards` gitlink, alongside this ticket and the map update.
