# 17 — Handoff to delivery pipeline

Type: task
Status: resolved
Blocked by: 01–16, 18, 19, 21 — all closed; unblocked
Map: [Secured Login App](../map.md)
Validated: approved — [validation record](../handoff/validation.md)

## Question

The way is clear; hand it off.

This is the map's destination. Nothing here is a decision — every decision was made in the tickets above. The work is to turn the map's **Decisions so far** into the artefact the delivery pipeline consumes, and then start it.

1. **Consolidate.** Assemble the closed tickets' answers into a single specification: the reconciled data model (with tombstones, password history and the renamed role), the endpoint inventory with its authorization matrix, the security configuration, the audit/logging contract, the test plan, and the pinned tech baseline. Every PRD acceptance criterion the authority order **changed** must be restated in its new form — a story whose criteria silently drifted is how a build fails review.
2. **State the deltas from the PRD explicitly**, with the ruling that caused each. A reviewer comparing the build to `prd/assessment-prd.md` will otherwise read every one of them as a defect.
3. **Hand off** to this repo's existing pipeline — `stories-to-issues` for the tickets and `/do-work` to build them, or `dependency-orchestrator` first if the slice ordering needs computing. Check what those skills expect as input before writing the artefact to fit them, not after.
4. **Record the definition of done** that [14](14-test-and-validation-plan.md) settled: which gates must run clean before the build is finished.

Wayfinder ends here. This map plans; it does not build. Closing this ticket closes the map.

**Amended by [15 — Tech baseline and module structure](15-tech-baseline-and-module-structure.md).** The baseline the pipeline needs is fixed: **Java 21, Spring Boot 4.0.x, Spring Security 7.0.x, Maven with a committed wrapper**; one repo with sibling `backend/` and `frontend/`; feature-first packages under `com.assessment.auth`; **Vite + React 19 + TypeScript + react-router v7 + axios**; the closed dependency set (one slot open for 07's rate-limit cache); the four-file configuration layout plus `db/changelog/`; environment-variable secrets with a committed `.env.example`; and the HTTPS/`TZ`/no-forwarding-agent deployment note in `backend/README.md`. 15 also graduated the map's **architecture-test** and **configuration/secrets** fog patches, so `arch-tests-plan` has real package names to write rows against.

## Answer

Resolved as a task. The handoff artefacts are written and validated; the pipeline itself is **blocked on two environment gaps**, both named below with their fixes.

**The map is closed. Artefacts are in [`../handoff/`](../handoff/).**

### What was produced

| Artefact | Content |
|---|---|
| [`handoff/spec.md`](../handoff/spec.md) | The consolidated specification — 13 sections covering the tech baseline, data model, the 21-row matrix, security configuration, password policy, lockout and rate limiting, reset, lifecycle, bootstrap, error contract, audit contract, test plan, and the tests that would otherwise ship silently |
| [`handoff/prd-deltas.md`](../handoff/prd-deltas.md) | Every PRD divergence with its causing ruling, in four classes: **changed**, **added**, **removed**, plus the data model and two documented limitations |
| [`handoff/secured-login-app.stories.yaml`](../handoff/secured-login-app.stories.yaml) | **24 stories, 141 acceptance criteria**, 6 groups, 1 release |
| [`handoff/README.md`](../handoff/README.md) | Run instructions, the two blockers, and the definition of done |

### Step 3 done first, as the brief instructed

"Check what those skills expect as input **before** writing the artefact to fit them, not after." Doing so changed the plan in three ways:

- **`convert-stories` is not needed.** It exists to turn an *unstructured* file into the stories schema. Ours is authored directly against `schemas/stories-schema.json` — which has `additionalProperties: false` at **every** level, so a free-form document would have been rejected. The file **passes the skill's own `validate-stories.py`** (exit 0, "VALIDATION PASSED") and an independent `jsonschema` check.
- **The schema is far leaner than this ticket assumed.** A story carries only `id`, `title`, `acceptance_criteria`, `group`, `release` — there is **nowhere** to put standards references, effort, or dependency edges. Those are *produced* by `dependency-orchestrator`, not supplied to it. So the specification could not be the pipeline input; it is the companion document, and the acceptance criteria had to be written to carry the decisions on their own.
- **The checked-in `run-config.json` is a trap.** `.agents/skills/dependency-orchestrator/artifacts/run-config.json` is a stale legacy shape — `platforms`, `story_count`, `feature_groups`, no `project_type`, and a `stories_path` pointing at another machine. It fails today's pydantic validator. The README uses `SKILL.md:173-188` instead and says why.

### Two blockers, both environment rather than planning

1. **`gh` CLI is not installed** — not on `PATH`, not in Program Files, chocolatey or scoop. `stories-to-issues` shells out to `gh label create` and `gh issue create` (`issue-creator.py:263-288`), so **no issues can be published**. And because `gh` is absent, **whether GitHub Issues are even enabled on this repo could not be determined** — worth checking before assuming this route works, since a disabled tracker means `stories-to-issues` does not apply at all.
2. **`python3` (3.14.6) lacks `pyyaml` and `pydantic`**, both required by `dependency-orchestrator`. The stories file was validated through a **throwaway venv at `%TEMP%\wfv`** so that nothing was installed into the user's environment.

**The orchestrator was deliberately not run.** It spawns up to 20 concurrent mapper agents — the expensive step on this whole map — and its only consumer (`stories-to-issues`) is blocked on `gh` regardless, so the output could not be used. Running it would have spent a large agent fleet to produce a file with nowhere to go. The exact command is in the README, ready.

### The definition of done, and the question that dissolved

14 settled it and this ticket carries it: **per-slice** gates inside `/do-work` (`mvn verify` including the Testcontainers PostgreSQL run, five ArchUnit rules, `semgrep`, frontend checks) and **once-before-done** gates (`im8-review`, `dependency-check-maven` at CVSS 7, `browser-test`, arch tests). **`pre-prod-check` is not required**, with reasons.

The map's original question — must `im8-review` or `pre-prod-check` run clean before 17 closes? — **has no answer, because when 17 closes there is no code**. 14 dissolved it rather than inventing a gate, and that dissolution is the honest end state.

### One operational fact worth more than it looks

**`docs/agents/issue-tracker.md` does not exist in this repo**, and `/do-work`'s `prepare-work.md:6-9` reads it to select the next unblocked issue when none is passed. So **every `/do-work` invocation must be given an explicit issue reference**, or it returns `Status: blocked` immediately. Either pass references one at a time, or create that tracker file first. Recorded here because it would otherwise be discovered as a mysterious block on the first build attempt.

### What the map delivered

21 tickets: 18 grilling, 2 task, 1 research-shaped. The decisions that mattered most were rarely the ones the tickets set out to make — **six tickets' stated premises turned out to be wrong**, and in each case fixing the premise changed the answer:

- **09**'s cross-origin/`SameSite` trilemma did not exist (`Std:238` bans the cookie pattern; `SameSite` ignores ports).
- **21**'s `ERROR`-versus-`WARN` contradiction dissolved on reading the full clauses.
- **07** counted two rate-limiting mechanisms where the standard mandates three.
- **11** found the PRD's own stub design prohibited by `Std:319`.
- **13** found the recipe's `SessionRegistryImpl` violates `Std:409`.
- **10** found the standard's "soft-delete" wording contradicted by its own recipe, resolved by its glossary.

Plus a recurring structural finding the map did not anticipate: **the recipes systematically undershoot their own standard** — `Clear-Site-Data` unimplemented, `.cors(...)` never wired, unbounded rate-limit maps, `sendError` in three filters, a `/csrf` endpoint with no cache headers. The standard is the clause list; the recipes are guides. 05 said so first and every later ticket found another instance.

**Wayfinder ends here.** This map planned; it did not build.
