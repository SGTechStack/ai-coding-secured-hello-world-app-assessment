---
name: ask-workflow
description: Ask which skill or path fits your situation. A guide over the full product development workflow — Requirements Gathering (PO/BA Harness) and Development (Dev Harness) phases, covering ideation through compliant delivery.
disable-model-invocation: true
---

# Workflow Guide

You don't know which phase you're in, or which skill to reach for next.

Two **phases** span the full product journey. The **requirements gathering** phase is where POs and BAs define what gets built. The **development** phase is where developers build it — running Matt Pocock's core engineering skills inside a compliance-gated harness that CoreX has extended.

**If the user hasn't stated which phase they're in, ask before continuing.**

---

## Phase 1: Requirements Gathering (PO/BA Harness)

The goal here is a **PRD** — the artefact that crosses the boundary to the dev team. Three paths lead there.

**Branch — which path fits?** Non-technical PO or standard feature: Path 1. Technical PO, stateful or complex feature: Path 2. New product, user research before requirements: Path 3.

**Path 1: Spec-Kit (Light / Functional).** Run **`/speckit-specify`** to produce `spec.md` with core user stories. Branch — do you need to validate with stakeholders visually? If so, choose a mockup (Stitch Skills or Figma as a static visual reference) or convert the stories directly into an HTML prototype via Stitch Skills — developers later scaffold it into Shadcn components via **`/html-to-shadcn`**. Commit and hand off.

**Path 2: Socratic / Prototyping (Deep / Technical).** Run **`/wayfinder`** to chart the work as investigation tickets on the issue tracker, resolving them one at a time until the way is clear. Branch — does a design question need a runnable answer (state machine, UI shape, a flow you have to see)? Detour through **`/prototype`** — keep the answer, discard the code. Once the way is clear, run **`/to-spec`** to compile the grilling decisions and ADRs into an early-draft TRD as the handoff artefact; follow immediately with **`/grill-me`** to verify alignment before handing off. (Dev Step 6 then refines this early-draft TRD rather than creating from scratch.)

**Path 3: CLARA / Design Thinking (Strategic).** Run CLARA iteratively — build personas and journey maps, define the root-cause problem statement, ideate solutions, and converge on the optimal UX. Outputs land as consolidated spec files in `specs/`, with an HTML prototype generated from the converged UX using Prizm Design components committed alongside — consumed by **`/html-to-shadcn`** before `/do-work` begins.

### Handoff

All three paths end the same way: the PO/BA commits spec files — and any HTML prototype — to the repo, then hands off to the Architect/Dev team. The **PRD** (or early-draft TRD for Path 2) is the artefact that crosses the boundary.

---

## Phase 2: Development (Dev Harness)

The route all dev work travels once a PRD is in hand. The harness sequences Matt Pocock's skills — **`/wayfinder`**, **`/to-spec`**, **`/to-tickets`**, **`/tdd`**, **`/code-review`**, **`/improve-codebase-architecture`**, **`/diagnosing-bugs`** — inside a structure that adds dependency orchestration, compliance auditing, and a self-steering build loop.

### Setup (once per project)

Before the first story, run three things. **`/setup-matt-pocock-skills`** configures the issue tracker, triage labels, and doc layout all skills assume — run once. **`/dependency-orchestrator`** sequences the full PRD: it builds a story-level dependency DAG, generates a PM delivery schedule and a wave-ordered Dev Delivery Plan, and highlights the relevant AppStandards per story; follow it with **`/grill-me`** to verify the plan before committing to it. **`/arch-tests-plan`** then **`/arch-tests-gen`** discover the architectural style, write `artifacts/arch-test-plan.md`, and compile boundary rules into runnable ArchUnit (Java) or dependency-cruiser (TS) tests. Optionally, register **Context7** as an MCP to give all skills access to live, version-specific library docs — it fires reactively when a named API or method surfaces in `/wayfinder` or `/do-work`, rather than relying on training-data snapshots.

With setup done, pick a story from the Dev Delivery Plan in wave order.

### Per-story: plan before you build

1. **`/wayfinder`** — chart the path for this story as investigation tickets on the tracker, resolving them one at a time until the way is clear. Pair with **`/codebase-design`** for module interface design. Use the AppStandards highlighted for this story in the Dev Delivery Plan as context. `/wayfinder` internally invokes **`/grilling`** (to stress-test decisions) and **`/domain-modeling`** (to pin down domain terminology and ADRs) — you do not call these directly during wayfinding.
2. **`/to-spec`** — enrich the PRD into a TRD: codebase context, ADRs, and technical constraints. Publish to the tracker. Follow immediately with **`/grill-me`**.

### Alignment hygiene

Run **`/grill-me`** immediately after every artefact-generating step — **`/dependency-orchestrator`**, **`/to-spec`**, **`/to-tickets`** — before moving forward. The artefact is only as good as the alignment check that follows it. Skipping it is how drift accumulates.

### Compliance gate → HITL 1

With the TRD published, run **`/spec-compliance`** to audit it against IM8/ARC policies, writing an HTML report to `artifacts/spec-compliance/spec-compliance-{timestamp}.html`. A human reviews the findings, approves fixes, and transitions the card to compliance-approved. Nothing moves to decomposition until this gate passes.

### Decomposition gate → HITL 2

With compliance approved, run **`/to-tickets`** to break the TRD into tracer-bullet vertical slices — end-to-end paths through schema, API, UI, and tests — published in dependency order; follow with **`/grill-me`**. Then run **`/issue-verification`**, which grills each issue's verticality, demoability, and granularity using **`/fry-me`**. A human resolves open questions and approves issues for execution. Nothing moves to execution until this gate passes.

### Execute: the do-work loop

With issues approved, the build begins. If the PO/BA supplied an HTML prototype, run **`/html-to-shadcn`** first to convert it into a Shadcn component scaffold before implementation starts.

Then run **`/do-work`** — the self-steering loop at the heart of the harness. It cycles **Implement → Review → Validate → Correct** until `Must-fix = 0` and all gates are green: compile, types, tests at ≥80% coverage, build, and a full compliance pass via **`/code-reviewer`** (semgrep, IM8, thermo-nuclear, framework reviews, mutation testing). The loop corrects itself without human intervention; it only exits when every gate passes. **`/do-work`** drives **`/tdd`** internally — one red-green slice at a time — exactly as Matt Pocock's **`/implement`** does, with the compliance layer added on top.

**Branch — which execution mode fits your constraints?**
- **Local Interactive** — run `/do-work` in a terminal as a pair programmer, owning your own Git branch.
- **Local Autonomous** — the agent runs `/do-work` in an isolated sandbox, committing locally when done.
- **Cloud/CI Autonomous** — a pipeline picks up an approved issue, runs `/do-work` end-to-end, pushes changes, opens an MR, and triggers `/code-review` to post feedback comments.

### Closeout

Once the loop passes, run the closeout sequence before pushing. Each step is optional but carries a quality cost if skipped:

- **`/improve-codebase-architecture`** — scan for shallow modules; pick a deepening candidate from the HTML report and walk a grilling session to redesign it. Its findings generate ideas you can take back into the next `/wayfinder` cycle. It's the survey that finds the candidates; **`/codebase-design`** is the bench you design the chosen one on.
- **`/arch-tests-gen-feature`** — instantiate feature boundary tests for the slice just built, replacing `<feature>` placeholders with real folder names.
- **`/code-review`** — two-axis review of the diff against the merge-base: **Standards** (repo conventions) and **Spec** (issue scope).
- **`/diagnosing-bugs`** — if tests fail or a regression surfaces during review, run this before pushing. It refuses to theorise until it has a tight feedback loop, then fixes with a regression test.
- **Impeccable suite** (for UI slices) — three targeted passes, each with a different lens:
  - **`/impeccable audit`** — technical quality: accessibility, performance, theming, responsive design, and AI slop anti-patterns.
  - **`/impeccable harden`** — resilience: edge cases, error states, empty states, i18n overflow, slow network handling.
  - **`/impeccable polish`** — visual finish: alignment, typography, spacing, interaction states, copy. Also useful in Phase 1 when reviewing a prototype before stakeholder sign-off.
- **`/browser-test`** — boot the stack, run acceptance-criteria scenarios derived from the issue in `agent-browser`, capture screenshots, write `artifacts/{feature}-browsertest.md`.
- **`/git-commit-skill`** — commit any residual artefacts accumulated across closeout using Conventional Commits, grouped by logical intent. Can run between any two closeout steps or once at the very end before pushing the MR.
- **`/e2e`** — turn approved user stories into repeatable Playwright browser tests with fresh Allure, video, trace, and handoff evidence. Use **`/e2e stability`** after a completed campaign to detect cross-run status flips.

---

## Matt Pocock's core engineering flow (solo dev / no harness)

For solo projects or work that doesn't need the full compliance harness, Matt Pocock's lighter-weight path still applies. The Dev Harness in Phase 2 builds on top of this flow — the skills here are what the harness orchestrates internally.

### Main flow: idea → ship

The route most solo work travels. You have an idea and want it built.

1. **`/grill-with-docs`** — sharpen the idea by interview. Start here when you **have a codebase**: it's stateful, retaining what it learns in `CONTEXT.md` and ADRs. (No codebase? Use `/grill-me` — see Standalone.)
2. **Branch — can you settle every question in conversation?** If a question needs a runnable answer (state, business logic, a UI you have to see), detour through a prototype, bridged by **`/handoff`** in both directions (see Crossing sessions):
   - **`/handoff`** out, then open a fresh session against that file,
   - **`/prototype`** to answer the question with throwaway code,
   - **`/handoff`** back what you learned, and reference it from the original idea thread.
3. **Branch — is this a multi-session build?**
   - **Yes** → **`/to-prd`** (turn the thread into a PRD) → **`/to-issues`** (split the PRD into independently-grabbable issues). Because the issues are independent, **clear context between each one**: start a fresh session per issue and kick off **`/implement`** by passing it the PRD and the single issue to work on.
   - **No** → **`/implement`** right here, in the same context window.

### Context hygiene

Keep steps 1–3 in **one unbroken context window** — don't compact or clear until after `/to-issues` — so the grilling, PRD, and issues all build on the same thinking. Each `/implement` then starts fresh, working from the issue.

The limit on this is the **smart zone**: the window (~120k tokens on state-of-the-art models) within which the model still reasons sharply. If a session approaches it before `/to-issues`, don't push on degraded — `/handoff` and continue in a fresh thread.

---

## On-ramps

A starting situation that generates work, then merges onto the main flow.

- **Something's broken** → **`/diagnosing-bugs`**. For the hard ones: the bug that resists a first glance, the intermittent flake, the regression that crept in between two known-good states. It refuses to theorise until it has a **tight feedback loop** — one command that already goes red on *this* bug — then fixes with a regression test. When the real finding is that there's no good seam to lock the bug down, its post-mortem hands off to **`/improve-codebase-architecture`**.

- **Bugs and requests piling up** → **`/triage`**. It moves issues through triage roles and produces agent-ready issues, which **`/implement`** (or **`/do-work`** in the harness) later picks up. Triage is only for issues **you didn't create** — bug reports, incoming feature requests, anything that arrives raw. Issues that `/to-issues` or `/to-tickets` produced are already agent-ready, so **don't triage them**.

---

## Crossing sessions

- **`/handoff`** — when a thread is full or you need to branch off (e.g. into a `/prototype` session), this compacts the conversation into a markdown file. You don't continue in place — you **open a new session and reference that file** to carry the context across. It's the bridge between context windows, in either direction. Use it when you want a **fresh session** but need the **current conversation preserved**.
- **`/compact`** (built-in) — stay in the **same conversation**, letting the earlier turns be summarised. Use it at **intentional breaks between phases**, when you don't mind losing the verbatim history. Don't compact mid-phase — the agent can lose its way. `/handoff` forks; `/compact` continues.

---

## Standalone

Off the main flow entirely.

- **`/grill-me`** — the same relentless interview as `/grill-with-docs`, but for when you have **no codebase**. Stateless: it saves nothing locally, builds no `CONTEXT.md`. Reach for it to sharpen any plan or design that doesn't live in a repo.
- **`/pragmatic-reviewer`** — pragmatic review of a design, proposal, or strategy grounded in inversion and second-order thinking. Separates Signal (must-fix flaws) from Noise (acceptable trade-offs). Reach for it ad hoc whenever you want a critical eye on a plan before committing to it.
- **`/teach`** — learn a concept over multiple sessions, using the current directory as a stateful workspace.
- **`/writing-great-skills`** — reference for writing and editing skills well.

---

## Path to Production *(Planned)*

Before a release is promoted to production, run **`/pre-prod-check`** — a single orchestrator that sequences all readiness gates and produces a consolidated sign-off report. Gates cover: API docs (`/gen-code-docs`), authorization matrix (`/gen-auth-matrix`), dependency vulnerability scan (`/dependency-vuln-scan`), threat model (`/owasp-threat-modeling`), technical architecture doc (`/tech-arch-doc`), full-codebase IM8/ARC compliance, observability readiness, and secrets/config audit. Each underlying skill can also be invoked independently to re-run a specific gate.

---

## Precondition

**`/setup-matt-pocock-skills`** — run before the first dev harness story to configure the issue tracker, triage labels, and doc layout all skills assume. Custom issue trackers also work.
