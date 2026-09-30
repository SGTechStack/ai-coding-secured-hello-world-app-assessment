---
name: issue-verification
description: Vets GitHub issues produced by planning skills (like /to-tickets or /stories-to-issues) to confirm each is a true tracer bullet (vertical, demoable, well-specified) and the whole set is consistent. Use this to verify quality before publishing issues to the tracker.
---

Verify Issues

## Process

### 1. Gather context
Collect every issue in scope: a fresh /to-issue run still in context, or issues fetched from the tracker by parent/label/explicit reference passed as an argument. Read full bodies, acceptance criteria, and blocked-by fields. Read CONTEXT.md and any ADRs in the area.

### 2. Build the set-level graph
Issues are a graph, not a list — editing one perturbs its neighbors, so hold the set as shared state outside any single issue:

* Nodes = issues, edges = blocked-by dependencies
* A coverage map of user story → slice(s)
* Topologically sort the graph (blockers first); detect cycles up front

This graph is what makes per-issue grilling safe — it's the artifact that absorbs the blast radius when a fix reslices an issue.

### 3. Grill in dependency order
Grill upstream→downstream so every issue's blockers have already settled by the time you reach it. For each ungrilled issue, run /fry-me focused on the checks below. Stage proposed changes against the in-memory graph — **do not write to the tracker yet.**

**Per-issue — is this a true tracer bullet?** Run the cheap discriminating checks first; dig into the rest only if the issue smells off:

1. **Verticality** — does the slice cut through every integration layer (schema → API → UI → tests), or stop at one? A horizontal slice in a vertical costume is the headline defect.
2. **Demoability** — when complete, can a human verify it *in isolation*? If confirming it needs another unfinished slice, it has an undeclared dependency.
3. **Acceptance-criteria quality** — is each criterion observable and testable, or vague ("works correctly")? Grill any criterion you couldn't write a test for.
4. **Granularity** — exactly one demoable behavior end-to-end? Multiple bundled = too coarse (split); can't demo alone = too fine (merge).
5. **Scope honesty** — does the body describe end-to-end behavior, or has it slid into layer-by-layer implementation notes?
6. **Prefactor placement** — is any prefactor isolated as its own first slice, or smuggled into a feature slice?
7. **Staleness** — does the body hardcode file paths or snippets /to-issue says to avoid (except a decision-rich prototype snippet)?
8. **Vocabulary & ADR conformance** — domain terms from CONTEXT.md, not invented names? If it contradicts an ADR, is the friction real enough to reopen it, or should the slice change to respect it?

### 4. Propagate the blast radius
When a grill changes an issue, diff what changed and propagate before moving on:

* Which dependents now have a stale blocked-by (e.g. a split into 3a/3b — does the dependent need one half or both)?
* Which user stories moved — is each still covered somewhere?
* Which sibling scopes now overlap?
* Did any staged edge introduce a cycle?

**Un-grill every affected downstream issue** (clear its mark, re-queue it). Surface the perturbed neighbors to the user before continuing: "Reslicing this splits it in two — 4 and 5 were blocked by it. 4 needs the API half, 5 the schema half. Confirm?"

Then mark the current issue grilled. The re-grill of a perturbed neighbor is cheap — it only changed in the dimension you touched, so it's a confirm, not a redo.

### 5. Verify set-level properties
Once per-issue grilling settles, grill the properties no single card can show — these are where the most valuable defects live:

* **Coverage** — is every user story landed in some slice? A story lost during splitting is invisible per-issue.
* **Overlap** — do any two slices claim the same behavior?
* **Dependency soundness** — is each blocked-by a real ordering constraint or artificial coupling that needlessly serializes parallel work? Any *undeclared* dependencies?

Any defect here re-opens the relevant issues for grilling (back to step 3).

### 6. Terminate, then implement
Loop steps 3–5 until **every issue is marked grilled AND no pending change un-marks another** — the graph has reached a fixpoint. Dependency ordering and acyclicity guarantee this terminates.

Only now write to the tracker. Publish the whole consistent set at once, in dependency order (blockers first) so blocked-by references resolve to real post-reslice identifiers. Use /to-issue's body template and publish discipline: correct triage label, and **do NOT close or modify any parent issue.**
