# Dependency Orchestrator

**Dependency Orchestrator turns user stories into a project schedule — with separate reports for PMs and developers, GitHub issue integration, and live progress tracking.**

---

## The Problem

A PM looks at 30 user stories and has no quick reliable way to answer how long, how many developers, or what risks to look out for during actual development. Similarly, a developer stares at the same stories and has to mentally trace which ones depend on which, what common infrastructure needs to exist, and how to sequence work across a team, before coming up with a projected timeline and work schedule. This planning is invisible, unorganized, and lives in someone's head. Without structure, the result is rework — stories built out of order, duplicated effort across tasks, agents burning tokens discovering context they should have been handed up front. 

The problem compounds with AI-assisted development — the rate of code being produced goes up, so integration conflicts surface sooner. Features get built against foundation pieces that don't exist yet, or against different assumptions about shared interfaces. The faster everyone ships, the more build order matters — and without an enforced sequence, the speed AI gives you actually works against you. It all gets done eventually, but it's messy, expensive, and slow.

**The Dependency Orchestrator** is an agentic skill that combines AI with deterministic algorithms to solve this. AI agents can now understand the domain, know what components a feature needs, and break down user stories into dev tasks with effort estimates — something that wasn't possible before. The orchestrator then runs real scheduling algorithms to produce the schedule, effort estimates, and role-specific reports.

---

## What You Get

Role-specific reports — PMs and developers get different views of the same data, each focused on what they actually need.

**For PMs:**
Hand it your user stories and get back an [**interactive Gantt chart**](output/screenshots/schedule-gantt.png) with a team size dropdown — switch it to instantly see how the timeline changes as you add or remove developers. The PM report includes a [**staffing comparison table**](output/screenshots/pm-report-teamsize.png) showing projected duration for every team size, so you can work backwards from a deadline and pick the team that fits your timeline. A [**projected completion timeline**](output/screenshots/pm-report-stories.png) shows when each story and epic is expected to finish, so you can communicate delivery dates per business area. An [**effort breakdown**](output/screenshots/pm-report-stats.png) by category lets you justify infrastructure investment, and a [**risk analysis**](output/screenshots/pm-report-teamsize.png) flags bottleneck stories before they surprise you. As developers close GitHub issues, run the reconciler to get [**live progress tracking**](output/screenshots/reconciled-pm-report-stats.png) — stories completed, percentage done, original projected vs remaining working days, and a burndown chart plotting actual completions against the projected curve. [**Per-epic progress**](output/screenshots/reconciled-pm-report-stories.png) shows DONE badges on completed stories so you can see which business areas are shipping and which are stuck.

**For Developers:**
Every story becomes a [**delivery card**](output/screenshots/dev-report-stories.png) with acceptance criteria, implementation-level tasks in build order, the full dependency chain, and applicable framework standards — all with a **one-click copy button** that dumps structured context ready to paste into an agent prompt. An [**inline dependency graph**](output/screenshots/dev-report-dependency-graph.png) expands on each card so developers can trace what blocks their work without switching views, and a [**color-coded allocation chart**](output/screenshots/developer-allocation.png) shows who works on what across the timeline.

The entire schedule is deterministic and traceable — every number comes from CPM analysis, Kahn's topological sorting, and greedy team simulations run against the dependency graph. New stories arrive mid-project? The orchestrator merges them into the existing graph and recomputes. The plan stays current without starting over.

---

## Three Skills, One Workflow

| Skill | What it does |
|-------|-------------|
| `/dependency-orchestrator` | Builds the story-level dependency DAG and generates PM + Dev reports |
| `/stories-to-issues` | Creates structured GitHub issues from the DAG — one per story, in delivery order |
| `/reconcile-stories` | Tracks progress by reconciling closed GitHub issues against the schedule |

```
User Stories
    |
    v
/dependency-orchestrator
    |-- project-dag.json      (machine-readable DAG)
    |-- processor-output.json (analysis data: CPM, simulations, waves)
    |-- pm-report.html        (PM-facing schedule & risk analysis)
    |-- dev-report.html       (Dev-facing delivery plan with task chains)
    |-- dev-report.md         (Markdown delivery plan)
    |
    v
/stories-to-issues
    |-- GitHub Issues          (one per story, in Gantt order)
    |
    v
/reconcile-stories           (run periodically as issues close)
    |-- Updated PM report     (with progress bar & DONE badges)
    |-- Updated Dev report    (remaining work re-prioritized)
    |-- Versioned snapshots   (track schedule evolution over time)
```

---

## How It Works

### 1. You provide user stories and platform choices

Tell the orchestrator your user stories and which platforms you're building for (web fullstack, mobile, etc). The orchestrator asks [targeted questions based on your role](output/screenshots/pm-prompt-questions.png) — PMs get asked about project name, platforms, release planning (single release or sequential releases), business groupings, and priorities; developers get asked about [team size](output/screenshots/dev-prompt-questions.png).

### 2. It derives every dev task through backwards mapping

For each user story, the orchestrator walks **right-to-left** from the end-state business goal back to the entry point, deriving every dev task node along the way:

```
[Order Fulfillment] --> [Payment Capture] --> [Inventory Reservation] -->
[Shipping/Tax Calc] --> [Cart Finalization] --> auth_service (foundation, stop)
```

Cross-cutting concerns (RBAC, session management, audit logging) are mapped to foundation nodes automatically. Nothing gets missed.

### 3. Foundation templates ensure infrastructure isn't forgotten

Platform-specific templates (backend, frontend, mobile) inject the universal scaffolding every project needs — structured logging, error handling, auth, API docs, architecture tests. These become infrastructure stories that gate feature work.

### 4. It builds a two-level dependency graph

The graph has two levels. The lower level is **dev tasks** — individual pieces of implementation work like "create patient data model", each with an effort estimate. The upper level is **user stories** — each story owns a set of dev tasks, and if any task in Story B depends on a task in Story A, then Story B depends on Story A.

PMs see stories and their dependencies. Developers see the same stories but can drill into the dev tasks inside each one.

### 5. It simulates the schedule

- **Critical path** — the longest chain of dependent stories sets the floor. The project can't finish faster than this chain no matter how many developers you add. Stories off this chain have slack and can slip without affecting the deadline.

- **Build order** — stories are sorted so nothing is scheduled before its dependencies. This prevents "we built checkout before auth existed" situations and catches circular dependencies early.

- **Team simulations** — the orchestrator simulates execution for 1, 2, 3, … N developers. Each developer picks up the next highest-priority ready story — PMs can set priority at the story or epic level (critical, high, normal, low) to influence which work gets picked up first. This produces projected duration per team size (e.g., 2 devs = 40 days, 3 = 28, 4 = 25) and generates the Gantt chart, developer allocation, and per-story start/end dates.

---

## GitHub Integration

### Creating Issues (`/stories-to-issues`)

Each user story becomes a [well-structured issue](output/screenshots/git-issue-sample.png) containing:

- "Auto-generated" badge and critical / priority flag
- Estimated effort
- Acceptance criteria (verbatim from user stories)
- Numbered dev tasks in dependency order with durations
- Standards references
- Dependency links (blocks / blocked by)

Issues are created in **scheduling order** (earliest start first), so the issue list naturally reflects the delivery sequence. Dry-run mode lets you preview before creating real issues.

```bash
# Preview first
/stories-to-issues   # dry-run by default

# Then create
/stories-to-issues   # confirm to create issues
```

### Tracking Progress (`/reconcile-stories`)

As developers close issues, run `/reconcile-stories` to recompute the schedule against reality.

**What happens under the hood:**
1. Fetches all issues from GitHub (open + closed)
2. Maps closed issues back to stories in the DAG
3. Sets completed story durations to zero
4. Reruns the CPM + simulation pipeline
5. Generates updated reports showing both completed and remaining work

The reconciled report adds a [**live tracking bar**](output/screenshots/reconciled-pm-report-stats.png) at the top — a single dashboard strip showing team size, stories done, progress percentage with a fill bar, original projected duration, and remaining working days.

[**The Gantt chart is a living schedule**](output/screenshots/reconciled-gantt.png) — it reshapes itself every time you reconcile. As stories close, the remaining work re-sequences around what's actually done, so each reconciliation gives you the current best projection of what's left and when it lands.

Reports are **versioned** (`pm-report-reconciled-v3-20260630T1422.html`), so you can track how the schedule evolves over time.

---

## Adding Stories Without Starting Over

Projects grow. New user stories arrive mid-build.

When the orchestrator detects an existing `project-dag.json`, it:
- Skips foundation template loading — infrastructure stories are already in the DAG
- Runs backwards mapping only for the new stories
- Merges new nodes and edges into the existing DAG
- Reruns the analysis pipeline

The result: new stories slot into the existing schedule at the right topological depth, with correct dependencies to both existing and new work. No need to re-derive everything from scratch. The existing story structure, node ownership, and foundation work are all preserved.

---

## Adjusting Estimates

If you disagree with a duration estimate, dependency, or story priority in the report, you don't need to regenerate the DAG from scratch. The DAG is agent-readable JSON — ask the agent to update the duration or priority for a specific story in `project-dag.json`, then rerun the analyzer and report generator.

Each story has a `priority` field (`critical`, `high`, `normal`, `low`) that controls scheduling order. The greedy scheduler picks higher-priority stories first, even over critical-path optimisation. Changing priority doesn't alter the dependency graph — only the order in which available stories are picked up by developers in the simulation.

The schedule, critical path, and team simulations are all recomputed from the updated DAG. It's a delta change — you're editing the existing graph, not rebuilding it from scratch.

---

## Multi-Release Planning

Large projects can be split into sequential releases. During setup, assign stories to releases (e.g., "Release 1: stories 1–80, Release 2: stories 81–150") and the orchestrator ensures each release completes before the next one starts.

Behind the scenes, stories in each release are analysed independently, then cross-release edges are injected so that all work in Release 1 finishes before any Release 2 work begins. The dependency graph, critical path, and schedule all reflect the release boundaries — PMs can see per-release timelines and developers get a build order that respects the sequencing.

The orchestrator handles up to 200 total user stories across all releases by splitting them into batches and processing all batches simultaneously. Each batch is analysed by a separate agent running in parallel, so a project with 200 stories doesn't take 4× longer than one with 50 — the wall-clock time stays roughly the same. For projects exceeding 200 stories, split into sequential releases of ≤200 each.

---

## Assumptions

The orchestrator makes several assumptions when generating schedules:

- **AI-assisted development** — Duration estimates assume developers are using AI coding tools. The defaults are calibrated for AI-assisted workflows; multiply estimates up if your team is not using AI assistance.
- **Duration defaults** — When user stories don't specify effort, the orchestrator assigns durations based on task complexity:

  | Size | Duration | Examples |
  |------|----------|----------|
  | Tiny | 0.25d | Config, env setup, glue code, single endpoint |
  | Small | 0.5–1d | Simple UI component, basic CRUD |
  | Medium | 1–2d | Feature with UI + API + tests |
  | Large | 3–4d | Complex domain logic |
  | Epic | 5–7d | Cross-cutting concerns spanning multiple layers |
- **Minimum task duration** — No task can be shorter than 0.25 days (roughly 2 hours). Even trivial setup tasks are floored at this minimum.
- **Working days** — All durations are in working days, not calendar days. The schedule does not account for weekends, holidays, or partial availability.
- **Full-time developers** — Team simulations assume each developer works full-time on the project. A developer finishes one story before picking up the next.
- **Sequential within a story** — Tasks within a single story are executed sequentially by one developer. Parallelism happens across stories, not within them.


---

## Quick Start

```
# 1. Build the DAG and generate reports
/dependency-orchestrator

# 2. Push stories to GitHub as issues
/stories-to-issues

# 3. Developers work, close issues as they ship

# 4. Reconcile progress and get updated projections
/reconcile-stories

# 5. New stories arrive? Run the orchestrator again — it merges automatically
/dependency-orchestrator
```

> **First run takes 20–30 minutes** depending on the number of user stories. The orchestrator performs backwards mapping, dependency analysis, and schedule simulation for every story — this is a one-time cost. Subsequent runs (add-on stories, reconciliation, re-analysis after estimate changes) are significantly faster because they build on the existing DAG.

---

## Future Additions

- **Static pages on GitHub/GitLab** — Publish the PM and dev reports as GitHub Pages or GitLab Pages, giving stakeholders a permanent URL to the latest project status without needing to open HTML files locally.

- **Auto-reconcile on issue close** — Set up a GitHub Action or GitLab CI pipeline that triggers `/reconcile-stories` automatically whenever an issue is closed. The pipeline regenerates the reports and deploys the updated static page, so the project dashboard is always current without manual intervention.

- **LLM model-aware estimation** — Allow users to specify which LLM model their coding agent uses (e.g., Claude Opus, Sonnet, GPT-4o, Gemini) and automatically adjust baseline duration estimates based on the model's known capability tier. Stronger models get tighter estimates; weaker models get wider margins.

- **Togglable duration buffer** — Add a percentage buffer slider to the PM report, similar to the team size dropdown. PMs select a buffer (e.g., +20%, +50%) and all durations scale accordingly, instantly updating the Gantt chart, staffing table, and projected timeline. This lets PMs stress-test the schedule against optimistic and pessimistic scenarios without regenerating the DAG.

---

## Technical Architecture

### System Design

The orchestrator separates AI work from deterministic work. AI subagents do the creative task of decomposing stories into dev tasks (backward mapping). Python scripts handle everything else — merging, graph analysis, scheduling simulation, and report generation. The main agent is a pure coordinator: it never holds story content in its context window, which lets the system scale to 500+ stories.

```
User Stories (file)
     |
     v
[Batching Subagent]          Reads stories, writes batch-1.json ... batch-N.json (<=25 each)
     |
     v  (all parallel)
[Backward Mapping Subagents] One per batch, all run simultaneously across all releases
     |
     v  (sequential by release)
[scripts/merge-batch.py]             Deterministic merge into project-dag.json
     |
     v
[scripts/dag-processor.py]           Story-level CPM, topo sort, team simulation -> processor-output.json
     |
     v
[scripts/report-generator.py]        HTML/Markdown reports from processor output
```

### Scripts

| Script | Purpose |
|--------|---------|
| `scripts/merge-batch.py` | Deterministic merge of batch outputs into the project DAG. Deduplicates nodes by exact ID match, applies release-aware edge rules, validates story coverage. |
| `scripts/dag-processor.py` | Derives story-level dependencies from the node graph, runs story-level CPM and greedy team simulation. Produces all data the report generator needs. |
| `scripts/report-generator.py` | Generates role-specific HTML/Markdown reports from processor output. Pure template rendering — no analysis logic. |

### Backward Mapping

The core AI algorithm is **Right-to-Left Business Dependency Backwards Mapping**. For each user story:

1. **Identify the end-state** — the final value-delivery outcome (e.g., "patient receives triage assessment")
2. **Walk backward** through strict functional prerequisites, justifying each link:
   ```
   [Order Fulfillment] -> [Payment Capture] -> [Inventory Reservation] ->
   [Shipping/Tax Calc] -> [Cart Finalization] -> auth_service (foundation node, stop)
   ```
3. **Map cross-cutting concerns** — RBAC (which roles need access?), session/state (TTLs, concurrency locks), and compliance/audit (immutable logging, state mutations). These reveal edges to shared infrastructure nodes that pure feature analysis misses.
4. **Emit nodes and edges** — each step becomes a dev task node; each dependency link becomes a directed edge.

Stories are batched (<=25 per batch) and all batches run **fully in parallel** as subagents. Each subagent receives the backward mapping rules and foundation templates inlined in its prompt, so it starts mapping immediately with zero setup tool calls. Multiple batches may independently derive the same foundation nodes — deduplication is handled entirely by the merge phase.

### Merge Phase (`scripts/merge-batch.py`)

Batches are merged sequentially into a single `project-dag.json`. The merge is deterministic:

- **Node dedup** — if a node ID already exists in the DAG, the duplicate is skipped
- **Edge rules by release context:**
  - Either endpoint in a **prior release** → edge skipped (cross-release ordering handled separately)
  - Endpoint in the **same release** from an earlier batch → edge kept
  - Dangling endpoints (referencing nodes that don't exist) → edge dropped
- **`--seal-release`** — after all batches for a release are merged, injects edges from all prior-release terminal nodes to all current-release entry nodes, enforcing "R(N) finishes before R(N+1) starts"
- **`--validate-stories`** — verifies every feature story from `stories.json` exists in the DAG; exits with error listing missing story IDs if any are absent

Release state (current release number, prior node IDs, prior terminal IDs) is tracked in a sidecar file (`project-dag.json.releases.json`).

### DAG Processor (`scripts/dag-processor.py`)

Pure Python, no AI. The node-level graph is used to derive story-level dependencies; all scheduling and timing operates at the story level.

**Node-level graph (used for dependency derivation only):**
- **Graph construction** — adjacency lists, reverse adjacency lists, in-degree counts from the edge list
- **Kahn's topological sort** — deterministic ordering with alphabetical tie-breaking. Detects cycles and reports the offending nodes. Used for build order within stories.
- **Transitive predecessors** — BFS from each node computes the full set of transitive predecessors, used for deriving story-level dependencies

**Story-level analysis:**
- **Story dependency derivation** — story B depends on story A if any of B's `owned_nodes` has a transitive predecessor belonging to A's `owned_nodes`
- **Story-level CPM** — critical path through stories, where each story's duration is its effort (sum of owned node durations). Tasks within a story are sequential by one developer.
- **Story-level simulation** — greedy scheduler at story granularity, respecting story dependencies and priority inheritance from feature groups. Source of truth for all scheduling data.
- **Delivery waves** — stories grouped by temporal overlap during simulation. A new wave starts when all previous stories finish before any new ones begin.
- **Max useful developers (knee of the curve)** — iterates team sizes, finds the last one where adding a developer saves >= 1 day (capped at 20). Beyond this point, extra developers sit idle because the critical path is the bottleneck.

### DAG JSON Format

The project DAG (`project-dag.json`) contains:

```json
{
  "project": "Project Name",
  "feature_groups": [
    { "id": "platform_setup", "name": "Platform Setup", "priority": "normal" }
  ],
  "nodes": [
    { "id": "be_auth_service", "name": "Auth Service", "duration_days": 2,
      "category": "backend", "status": "pending" }
  ],
  "edges": [
    { "from": "be_auth_service", "to": "be_triage_routes" }
  ],
  "user_stories": [
    { "id": "AFW-101", "title": "As a medic, I can triage a patient",
      "group": "patient_intake", "owned_nodes": ["be_triage_service", "be_triage_routes"],
      "category": "feature", "priority": "high",
      "verifiable": "Triage form submits, record persists" }
  ]
}
```

- **`edges`** — `from` is the prerequisite, `to` is the dependent. `from` must complete before `to` can start.
- **`owned_nodes`** — the full backward chain of dev tasks derived from a story. The processor uses this to compute `testable_day` and `dev_tasks_ordered`.
- **`status`** — tracks implementation progress (`pending` / `completed`). The reconciler sets nodes to `completed` when their owning story's GitHub issue is closed.
- **`priority`** — `critical`, `high`, `normal` (default), `low`. Stories inherit their group's priority unless explicitly overridden. Affects scheduling order, not the dependency graph.
- **`verifiable`** — acceptance criteria copied verbatim from user stories. Used in reports and GitHub issues.

### Fullstack Conventions

For projects spanning multiple stacks, the orchestrator uses node ID prefixes to avoid collisions:

| Stack | Prefix |
|-------|--------|
| Frontend | `fe_` |
| Backend | `be_` |
| Mobile | `mob_` |

Cross-stack edges are created when backward chains cross layers (e.g., `fe_api_client` -> `be_api_routes`). Prefixes distinguish task type, not team assignment — all developers work across stacks and the scheduler treats all nodes as a single shared pool.

### Multi-Release Support

Stories can be assigned to sequential releases. During merge:

1. All batches for release N are merged with `--release N`
2. `--validate-stories` confirms all feature stories are present
3. `--seal-release` injects terminal-to-entry edges between releases
4. Process repeats for release N+1

Each seal is self-correcting for 3+ releases — previously sealed terminals already have outgoing edges, so only the most recent release's terminals are found.



