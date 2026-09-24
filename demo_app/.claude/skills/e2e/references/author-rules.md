# Story author rules

Read this and [Allure evidence](allure-evidence.md) before **1C–1D**. The executor owns frozen evidence, reports, review, and teardown.

## Recovery boundary

- Classify an incomplete run before retrying: **FAIL** when the reached product contradicts the story; **SKIPPED** when an authorised prerequisite cannot be established; **technical fault** when the spec, harness, environment, or evidence is defective. The source story controls: a live contradiction is FAIL; never change campaign artifacts to fit the app. Diagnose a technical fault once, make one understood fix, then use fresh evidence; never blind-retry.
- Run `<skill-root>/scripts/phase_timer.py --help` once, then continue it for 1C–1D; it writes timing files automatically—never edit/read them during the campaign. Preflight failures never justify a stack rebuild.
- Reuse one disposable diagnostic stack while diagnosing. If a failed run created data, recreate it or apply the documented full reset before final evidence. After three attempts without new evidence, write and validate `diagnostic-handoff.json` before a new context continues.
- Start by validating `discovery-handoff.json`. Reuse its healthy recorded stack after one health check; do not redo story analysis, broad source searches, or discovery. If one genuinely missing fact appears, check that fact live, cite it in the spec's `Data`, then continue. A foundation gap still returns to the coordinator.

```powershell
python "<skill-root>/scripts/verify_allure_e2e.py" --verify-diagnostic-handoff <diagnostic-handoff.json>
```

## 1C. Author the spec

1. Ask one concise user question only if two natural readings both fit and the story/product/UI cannot resolve them. A documented clause that differs from live behaviour is not an ambiguity: it is FAIL. Record other material assumptions and continue.
2. Never weaken a failing assertion. `PASS` requires the visible outcome; `FAIL` requires a reached contradictory surface; `SKIPPED` requires an authorised missing prerequisite and real blocked action. Missing/incorrect reachable UI is FAIL, not SKIPPED.
3. Each scenario owns its data and maps one AC only. Do not depend on test execution order or assert adjacent-story routes/headings/actions. Use exact copy only when the story requires it; otherwise use the smallest story-vocabulary invariant—never invented synonyms, formats, success paths, or broad regexes.
4. Use the story actor unless the clause explicitly names another. Preserve actor, subject, polarity, quantifier, named surface, and state transition. A control being enabled is not proof of an action; a blocked control is proved by its disabled state, never a bypassed click.
5. Assert the required rendered outcome—not a response code, source fact, count-only proxy, or inferred domain status. Use full visible sets and retain their observed values for `only`/`all`/ordering claims; use before-and-after evidence for transitions. Before a rendered-UI assertion or blocker screenshot, scroll the asserted value into view so it is captured in the screenshot, trace, and video. For charts, canvases, maps, or long tables a reviewer must inspect as a whole, also attach an element screenshot; it supports—not replaces—the runtime assertion. Omit this for nonvisual state. If the requested value/status is not rendered, skip that claim rather than weakening it.
6. Use MCP-observed roles, labels, and visible text in Playwright Test; never test-id or implementation-derived expected copy. Cite each permitted-test oracle in `Data`. When the story explicitly requires identity/ownership/attribution, match the rendered identifier to an independently established identity; otherwise do not invent that stronger claim. For native required-field validation, prove `validationMessage` and focus. Prefer event-driven waits. For CSS transitions, wait for the concrete rendered end state, never a generic absence heuristic. Recheck dismissible overlays immediately before state-changing clicks: absence may be tolerated, but a visible overlay must dismiss or fail fast. For stacked dialogs, prove the locator is unique and prefer the intended field/button over a generic dialog-role locator. Inspect a timeout before adding a bounded non-story overlay handler; use a handler only for a unique, stable locator; never force-click or handler away a story-required overlay.
7. Keep terminal/global-state mutations isolated. Seed near thresholds with harness-owned setup, then prove the smallest UI transition; never O(N) UI setup loops. Use one isolated stack/database whenever scenario state is pre-existing, global, or unsafe to own on the shared stack.
8. For several specs, clone only the boilerplate. Rewrite every scenario plan, mapping, review claim, and assertion.
9. Compose scenario code straight into the target spec file under `e2e/tests/`; never stage scenario prose or code in a separate scratch file (repo root or elsewhere) — a draft buffer outside the target wastes a pass and risks debris outside version control.
10. For a route intentionally delayed then fulfilled or continued, wait for it to settle before `unroute`, page/context close, or teardown; keep its in-flight assertion before that wait. Do not apply this wait to intentional abort/pending routes.

## 1D. Preflight and author handoff

Complete the Allure Test-plan/metadata contract in `allure-evidence.md`, then time preflight **before any final stack restart or evidence run**:

```powershell
python "<skill-root>/scripts/verify_allure_e2e.py" --preflight <all-story-specs...> --story-analysis <story-analysis.json> --foundation-manifest e2e/e2e-foundation.json
```

Fix every finding. Then perform a bounded semantic review: check every claim against live UI, permitted test data, or isolated setup; for story-critical actor, action, polarity, quantity, named-surface, or transition language, confirm the assertion proves that exact meaning. Rendered values need rendered-value proof; global sets and transitions need full/before-after proof.

Freeze the story analysis, specs, skill, verifier, and foundation. Any later change discards evidence and requires new preflight plus a fresh run. Write `author-handoff.json` only after passing preflight, containing exact paths and SHA-256 for analysis/specs, command/timestamp, phase timings, workspace status, author context tokens, and chosen execution mode. Validate it:

```powershell
python "<skill-root>/scripts/verify_allure_e2e.py" --verify-story-analysis <story-analysis.json>
python "<skill-root>/scripts/verify_allure_e2e.py" --verify-author-handoff <author-handoff.json>
```

If the author context is below 100k tokens, it may continue by reading [executor rules](executor-rules.md). At 100k or more, return only the author-handoff path; the coordinator delegates a fresh executor.
