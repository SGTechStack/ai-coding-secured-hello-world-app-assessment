# 20 — Agent tooling: committed or ignored

Type: task
Status: resolved
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

## Answer

Resolved as a task, one round. Decisions delegated by the user to the orchestrating session after the evidence was presented; recorded here as the agent's calls, not a grilled exchange.

**All four paths are ignored. `.gitignore` is written and is the only thing this ticket commits.** `.scratch/` stays tracked.

### The deciding fact is 06's, repeating

`skills-lock.json` names exactly two sources (`grep -o '"source": "[^"]*"' skills-lock.json | sort -u`):

- `SGTechStack/ai-coding-workflow-d2` → **404** unauthenticated
- `mattpocock/skills` → **200**

and `SGTechStack/ai-coding-secured-hello-world-app-assessment` (this repo, `origin`) → **200**.

So committing `.agents/skills/` discloses a **private SGTechStack repo into a public one** — the identical finding 06 made about `App-Standards/`, reached independently and on the same evidence shape. This is the whole answer; the ticket's framing ("these are *method*, and the map is a committed artefact, so there is a real argument the skills travel with it") is a good argument that simply loses to the disclosure boundary.

**The tree cannot be split along the source boundary.** The two sources are interleaved across ~40 skill directories with no manifest partitioning them; separating them means hand-auditing every skill against `skills-lock.json`. Not worth it to publish a toolchain no external reader can install anyway, since half its sources 404 for them.

### Two facts that make this cheaper than it looks

- **`.claude/skills/*` are absolute symlinks, not files.** `ls -la .claude/skills` shows every entry as `lrwxrwxrwx … -> /c/Users/TaniaKoh/Downloads/code/ai-coding-secured-hello-world-app-assessment/.agents/skills/<name>`; `find .claude -type f` returns **zero** files against 16K of links. Committing `.claude/` would commit this machine's absolute paths, which resolve nowhere else. Independently decisive, and it also proves `.agents/` is the live tree.
- **`agent/` is a redundant duplicate of `.agents/`.** `diff -rq .agents/skills agent/skills` yields **no "Only in agent/" entries** — 193 files against 296, every one present in `.agents/`, differing only in YAML frontmatter line-wrapping. Nothing is lost by ignoring it.

`skills-lock.json` is ignored too. It is our own file and contains no skill content, which is the argument for keeping it — but it is useless to anyone who cannot resolve `ai-coding-workflow-d2`, and it names the private repo in a file whose whole purpose is to be read. `samuelwong` ignores it; we match.

### The deviation from both siblings: `.scratch/` stays tracked

`samuelwong` ignores `.scratch/`; this branch tracks it deliberately, and that is the one line where we depart. On this effort the map **is** the deliverable — planning is the destination, per the map's "Planning only" note. Written into `.gitignore` as a comment so a later session does not "fix" it by copying the sibling.

`mingliang` ignores `.claude/`, `.agents/`, `.kiro/`; `samuelwong` adds `skills-lock.json`, `.scratch/`, `artifacts/`. **Neither mentions `agent/`** — both predate it or never had it. `.kiro/` is carried forward from both siblings although it does not exist here, since it costs nothing and the next person to install Kiro will not think to add it.

### Left undone, deliberately

**`agent/` is ignored but not deleted.** 4.1 MB of proven duplicate sitting in the working tree. Deleting it is a working-tree change this ticket has no mandate for, and while `.claude/`'s symlinks prove `.agents/` is live, **nothing here proves no tool reads `agent/`** — the `agent/skills/**/SKILL.md` paths appear in the skill listing this session was given, so something plausibly does. Recorded as a known wart rather than removed silently. If a later session confirms nothing reads it, deleting it is a one-line commit.

### Consequences

- The first commit is now unblocked on both counts: 06 settled `App-Standards/`, this settles the rest.
- **A clone of this repo cannot reproduce the toolchain that produced the map.** That is an accepted cost, not an oversight — it was already true for `App-Standards/` after 06, and it is the price of the repo being public.
- `artifacts/` (from `samuelwong`'s list) is **not** ignored here: no such directory exists yet, and several skills in the tree write reports into `artifacts/`. Left for whichever ticket first generates one — most likely 14 or 17.
