# Story executor rules

Read this and [Allure evidence](allure-evidence.md) after receiving a passed `author-handoff.json`. Do not reinterpret the story, change frozen author artifacts, modify foundation files, or commit.

## Recovery boundary

- Treat **FAIL** as a reached product contradiction and **SKIPPED** as an unreachable authorised prerequisite. A spec/harness/environment/reporter/selector/timing fault is technical: diagnose once, make one understood fix, preflight again, then create fresh evidence. Never blind-retry or relabel a technical fault as a story verdict. When one scenario fails, diagnose and rerun only that spec/title on the disposable stack until understood; then reset/recreate state and run the full frozen suite once. A shared-fixture failure is one technical cascade, not several story failures: fix the fixture or isolate the fragile scenario first.
- Run `<skill-root>/scripts/phase_timer.py --help` once, then continue it for 2A–3; it writes timing files automatically—never edit/read them during the campaign. Finish the current phase before final verification: every entry needs `ended_at`. The final verifier requires successful preflight before the final run and Allure timestamps inside that run window.
- Diagnose on one disposable stack. If a run created data, recreate the isolated stack or apply a documented full reset before final evidence. After three attempts without new evidence, write/validate `diagnostic-handoff.json` and stop for a fresh context.
- A foundation gap a small harness change would unblock (a protected fixture that will not provision, a shared service that fails the app's real call) is not a technical fault to fix here — at the first failed integration call, before running the full suite, stop and hand back `foundation-gap.json` to the coordinator, same as an author would.

## 2A. Verify and freeze

Validate the author handoff and rerun preflight, then prepare a new `run-manifest.json` with frozen analysis/spec SHA-256, unique campaign suite, results/report paths, stack project, and the exact evidence-run command. At final-run launch, capture `not_before` immediately before Playwright, set `isolation_id` to `null` unless an actual worker isolation ID is passed, then validate the completed manifest. Do not edit frozen files. A rerun gets a new campaign id/manifest.

```powershell
python "<skill-root>/scripts/verify_allure_e2e.py" --verify-author-handoff <author-handoff.json>
python "<skill-root>/scripts/verify_allure_e2e.py" --preflight <all-story-specs...> --story-analysis <story-analysis.json> --foundation-manifest e2e/e2e-foundation.json
# Run immediately after recording `not_before`, before final Playwright:
python "<skill-root>/scripts/verify_allure_e2e.py" --verify-run-manifest <run-manifest.json>
```

## 2B. Produce final evidence

1. Use the project’s documented isolation lifecycle. Before booting it, check persona/login route, browser origin/CORS, ports, and teardown. Ask before stopping an occupied compatible port. Prove login before creating data.
2. Use one fresh isolated final stack by default. Reset/seed each independent scenario through documented harness-owned setup and verify its exact starting state. Do not rebuild just because prior scenarios mutate data; rebuild only when reset cannot prove cleanliness, infrastructure changes, or the environment is unhealthy.
3. Run one worker with zero retries, trace/video/failure screenshots on, separate discovery recordings, and real browser channel. This binds final frozen evidence so retries cannot mask a flake. During disposable diagnosis only, one scenario may use retries to characterise a flake; never treat that output as final evidence. Keep one trace/video attached to each scenario. Use `E2E_SLOWDOWN=1|2|4|8|16` only for optional capture pacing; normal video and trace remain canonical.
4. Preserve normal-speed video. Optional slowed copies never replace it; never modify a trace zip.
5. A full evidence run (stack boot + suite) outlasts a foreground timeout — launch it detached and poll for completion, not a fixed wait. Use the frozen Allure command, which writes directly to empty campaign paths, never shared results. Before launching, confirm no earlier runner for this harness/campaign paths survives (a killed foreground run can orphan a child that flushes stale partial results there); never stop another project’s runner. After the run, before the report, confirm the raw result count equals the frozen scenario count exactly.
6. Freeze the exact evidence-run command in the run manifest at 2A (the project's documented command, its documented directory) and reuse it verbatim every attempt — never re-derive it per run. Before the frozen suite, one navigation to the configured baseURL (an MCP navigation or a curl on the frontend root) must render the app; a wrong baseURL or missing config fails every scenario slowly instead of failing fast.

## 2C. Generate, verify, inspect

1. Use a new timestamped campaign directory. Preserve raw results separately from the cumulative portal; never mix/delete earlier evidence or use the portal as verifier input.
2. Generate with the foundation-pinned local **Allure 3** `allure awesome` CLI, no `--reporter` override, and the hierarchy required by `allure-evidence.md`. Never use legacy `allure-commandline`, hand-write Allure summary files, or trust UUID ordering.
3. Use the launch timestamp recorded in the manifest, then run the verifier against fresh raw results, the generated report, manifest, phase timings, suite, and `--not-before`. It must exit 0.
4. Start/reuse the toolkit report portal, then run its automatic evidence check. If a trace link fails to load, restart the portal once and repeat that check; never alter raw evidence. Manually inspect Trace Viewer only for FAIL/SKIPPED or after a foundation/report-tool change. Leave the portal running.

## 2D. Accept the result

1. For a FAIL/SKIPPED, first rule out false verdicts: intended persona and starting state, relevant action/surface reached, and no selector/timing/reporter/stack/fixture fault. A valid skip reaches the actual blocked action and documents the unmet prerequisite. Explain FAIL from the required visible outcome versus the observed visible outcome—never speculative implementation cause or generic Playwright error text.
2. If any atomic claim lacks proof, reject the evidence, return to author preflight with the precise deficiency, and create fresh evidence. Use judgment for story-critical actor, action, polarity, quantity, named-surface, and transition language; do not demand extra identity binding unless the story requires it. Do not amend a report/handoff to rescue it.
3. Write concise `<campaign>/handoff.md`: verbatim story, AC-to-result table, evidence-based FAIL/SKIPPED explanations, frozen spec/raw results/report paths, portal URL, verifier command/outcome, and ranked timings. Never include raw logs or reasoning.

## 3. Teardown

Use the paired isolated teardown. It must stop only its recorded processes and fail closed on an identity mismatch. Restore and health-check the shared stack. Do not stop the report portal.
