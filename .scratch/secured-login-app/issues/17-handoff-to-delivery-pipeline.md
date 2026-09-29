# 17 — Handoff to delivery pipeline

Type: task
Status: open
Blocked by: 01–16, 18, 19, 21 — all closed; unblocked
Map: [Secured Login App](../map.md)

## Question

The way is clear; hand it off.

This is the map's destination. Nothing here is a decision — every decision was made in the tickets above. The work is to turn the map's **Decisions so far** into the artefact the delivery pipeline consumes, and then start it.

1. **Consolidate.** Assemble the closed tickets' answers into a single specification: the reconciled data model (with tombstones, password history and the renamed role), the endpoint inventory with its authorization matrix, the security configuration, the audit/logging contract, the test plan, and the pinned tech baseline. Every PRD acceptance criterion the authority order **changed** must be restated in its new form — a story whose criteria silently drifted is how a build fails review.
2. **State the deltas from the PRD explicitly**, with the ruling that caused each. A reviewer comparing the build to `prd/assessment-prd.md` will otherwise read every one of them as a defect.
3. **Hand off** to this repo's existing pipeline — `stories-to-issues` for the tickets and `/do-work` to build them, or `dependency-orchestrator` first if the slice ordering needs computing. Check what those skills expect as input before writing the artefact to fit them, not after.
4. **Record the definition of done** that [14](14-test-and-validation-plan.md) settled: which gates must run clean before the build is finished.

Wayfinder ends here. This map plans; it does not build. Closing this ticket closes the map.

**Amended by [15 — Tech baseline and module structure](15-tech-baseline-and-module-structure.md).** The baseline the pipeline needs is fixed: **Java 21, Spring Boot 4.0.x, Spring Security 7.0.x, Maven with a committed wrapper**; one repo with sibling `backend/` and `frontend/`; feature-first packages under `com.assessment.auth`; **Vite + React 19 + TypeScript + react-router v7 + axios**; the closed dependency set (one slot open for 07's rate-limit cache); the four-file configuration layout plus `db/changelog/`; environment-variable secrets with a committed `.env.example`; and the HTTPS/`TZ`/no-forwarding-agent deployment note in `backend/README.md`. 15 also graduated the map's **architecture-test** and **configuration/secrets** fog patches, so `arch-tests-plan` has real package names to write rows against.
