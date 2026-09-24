---
name: e2e
description: Set up or run trustworthy Playwright E2E evidence for the next documented user story.
---

# Story E2E

Work from the repository root. Use `/e2e` for a story campaign or `/e2e stability` for an existing campaign's stability sweep.

Resolve `<skill-root>` to the directory containing this file and substitute it
in commands. Roles need a fresh isolated context and the project-configured
Playwright browser tool. Use medium effort where the host exposes it. Roles
inherit the coordinator's model. If the host offers a 1M variant of that same
normal coding tier, select it for the coordinator before starting the skill;
never change tier solely for context length. The handoffs remain mandatory
either way.

## Decide what to do

1. Check `e2e/e2e-foundation.json` with:

   ```powershell
   python "<skill-root>/scripts/verify_allure_e2e.py" --verify-foundation e2e/e2e-foundation.json
   ```

   If invoked as `/e2e stability` and the foundation passes, read [stability rules](references/stability-rules.md), run the sweep against an already-completed campaign, and stop. Do not select or author a new story.

2. If the manifest is missing or invalid, read [foundation rules](references/foundation.md) in full. For foundation-only setup, do not read `references/{discovery-rules,author-rules,executor-rules,workflow,allure-evidence}.md` or `scripts/verify_allure_e2e.py` unless a failed foundation command's output points to that file. Set up the project foundation only: create `e2e/tests/`, `e2e/fixtures/`, and `e2e/scripts/`; create `artifacts/e2e/` for generated campaign evidence; smoke-test and review the foundation; then commit only the verified foundation if the user requested a commit. Do not start a story campaign in the same invocation.

3. If the manifest verifies, inspect documented stories and verified campaign evidence under `artifacts/e2e/campaigns/`. Coverage requires raw results, a final verifier pass, and `handoff.md`; never trust a portal, old spec, or green summary alone. Recommend the next user story: start with the first uncovered story in an untested feature; otherwise recommend continuing the partially covered feature. Name the proposed story or feature and list its acceptance criteria. For a feature/batch, list every story and wait for explicit confirmation.

   Read [the workflow overview](references/workflow.md) when a concise phase or recovery map is useful.

4. After confirmation, delegate one story at a time; never run stories in parallel. Use installed host-role bindings when available. Otherwise launch a fresh generic subagent with only its role, `skill_root`, story or handoff path, and required rule paths—never coach an expected outcome. For **four or more documented ACs**, the discoverer runs 1A–1B and returns a validated `discovery-handoff.json`; a fresh author receives only that path for 1C–1D. For a smaller story, the author receives the story and runs 1A–1D after reading [discovery rules](references/discovery-rules.md), author rules, and Allure rules. Below 100k author-context tokens, it may continue with [executor rules](references/executor-rules.md); at 100k or more, a fresh executor receives only the passed `author-handoff.json` and owns final evidence. Store each campaign in `artifacts/e2e/campaigns/<utc-id>-<story-slug>/`; store frozen analysis, discovery/author handoffs, specs, raw results, report, and timings there. Keep runnable specs in `e2e/tests/`.

   If a delegated agent hands back `foundation-gap.json` instead of a passed handoff, route it. **A small harness fix that unblocks a real product outcome** → delegate one fresh agent the [foundation-fix task](references/foundation.md#foundation-fix-task) with the gap report; when it hands back, gate the fix with one fresh agent (not the fixer): it checks the fix against the app's real integration code (`foundation-gap.json` plus that code — a bounded "does the dependency now reach the state the AC needs, and revert cleanly", not a design review), then runs `--verify-foundation` and the isolated smoke and reports a verdict. A wrong foundation fix is worse than a SKIP: on a mechanical miss, resume the fix agent once; a contract mismatch or any doubt → the user. On a clean verdict, re-delegate the story to a fresh author from analysis (the environment changed, so the first author's discovery is stale). **An inherent environment limitation** → tell the reporting author to resume and author the full story, with the blocked clause as a genuine skipped scenario. **A redesign or anything unclear** → stop and ask the user. Re-delegate a story for foundation reasons at most twice.

5. Use bundled scripts through `<skill-root>`. Do not copy or modify this skill inside the project. Foundation setup installs only a host adapter when one is needed.

6. Leave the report portal running after a completed campaign. Start it with:

   ```powershell
   python "<skill-root>/scripts/serve_e2e_reports.py" start --artifacts-root artifacts/e2e --harness-root e2e
   ```

   Tell the user the printed URL and the feature, user story, and AC/scenario to open in Allure Suites.
