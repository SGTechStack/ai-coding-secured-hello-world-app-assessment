# Story discovery rules

Read this before **1A–1B**. Discovery owns story analysis and live facts; it never writes specs, runs preflight, or creates final evidence.

## Recovery boundary

- Classify an incomplete run: **FAIL** when a reached product contradicts the story; **SKIPPED** when an authorised prerequisite cannot be established; **technical fault** when the harness, environment, or evidence is defective. The source story controls: preserve a contradictory clause in analysis/handoff and plan FAIL; never rewrite campaign artifacts to fit the app. Diagnose a technical fault once; never blind-retry.
- Run `<skill-root>/scripts/phase_timer.py --help` once, then use it for 1A–1B. It writes timings automatically; do not read or edit them during the campaign.
- A **foundation gap** is only a small harness change that reaches a state an AC needs. Write `foundation-gap.json` with the dependency, app call, observed/required state, and raw evidence, then return it. A private backend-to-backend request/header/body/log claim is a genuine skipped scenario, not a foundation gap, when only a test-only echo/read-back/logging backdoor could expose it; name the unit or HTTP-client-contract layer that should prove it instead.

## 1A. Analyse

1. Create and validate `story-analysis.json`: exact story; `acceptance_criteria` with each `AC<n>` and its full verbatim scenario; every atomic claim's AC, verbatim clause, actor, visible outcome, surface, observation; plus allowed files, base URL, startup command, and isolation. Include every testable `MUST`/`SHALL` clause; if one has no scenario, flag it before calling the requirement complete.
2. Decompose labels, counts, polarity, ordering, thresholds/ranges, contrast, and accessibility. A Given/When setup surface is not an AC unless the story says so. Record boundary coverage for finite integer bands.
3. Tag each claim as caller/UI-visible, browser-network-visible, or private backend-to-backend. A private claim with no already-permitted observable surface is planned as a real skipped scenario; do not skip from source-only diagnosis.

## 1B. Discover and hand off

1. Verify the foundation manifest and start the documented discovery stack. Confirm app reachability and every external integration an AC depends on; a listening port is not a working integration.
2. Use source/tests only for setup, fixture, route, data-shape, or locator hints. Derive expectations from the story and validate final labels, roles, routes, and values live with Playwright MCP. Record each resolved fact immediately; use targeted reads/searches, never repeated whole-file sweeps.
3. Search existing fixtures, verify the persona in the UI, and use a fresh browser context to switch persona. Keep diagnostics out of final specs. If a prerequisite is inherently unavailable, plan a skipped scenario; if a small harness change would unblock it, return `foundation-gap.json`.
4. Write and validate `discovery-handoff.json` in the campaign directory. It contains the hashed analysis, verified foundation path, current stack identity/base URL, claim-by-claim live facts, fixture/setup constraints, source-path pointers, phase timings, and discovery context tokens. Keep facts concise; do not copy source files or reasoning.

Use this shape; every `claim_facts` entry must match one analysis claim's AC and visible outcome:

```json
{"version":1,"story_analysis":{"path":"...","sha256":"..."},"foundation_manifest":"e2e/e2e-foundation.json","stack":{"base_url":"...","lifecycle_id":"...","project":"..."},"claim_facts":[{"ac":"AC1","visible_outcome":"...","facts":[{"kind":"role|label|route|value|constraint","value":"...","live_evidence":"MCP observation"}]}],"fixtures":["..."],"constraints":["..."],"source_paths":["..."],"phase_timings":".../phase-timings.json","discovery_context_tokens":0}
```

The linked analysis uses `"version": 2` and contains `"acceptance_criteria":[{"ac":"AC1","text":"Given … When … Then …"}]`; each `text` is copied verbatim from the documented story.

```powershell
python "<skill-root>/scripts/verify_allure_e2e.py" --verify-story-analysis <story-analysis.json>
python "<skill-root>/scripts/verify_allure_e2e.py" --verify-discovery-handoff <discovery-handoff.json>
```

Return only the validated handoff path (or `foundation-gap.json`).
