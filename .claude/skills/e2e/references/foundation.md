# Foundation setup rules

## 0. Foundation setup (once per project)

### New-project bootstrap

Do this once, before the first story campaign. It is framework- and hosting-neutral: use the project's own commands and tools rather than copying another project's scripts.

1. Create `e2e/README.md` with a short **project profile**: install/start command, health check, browser base URL, test accounts/auth fixture, designated test environment, and report directories.
   Add `artifacts/e2e/` to `.gitignore` by default; it is generated local evidence, not runnable test source.
2. Document a harness-owned **reset/isolation contract**. It must start a uniquely identified test lifecycle, reset only its designated test data, print its base URL/lifecycle ID, and tear down only resources it created. State-changing harness commands must distinguish benign tool diagnostics from a real non-zero failure; when a command declares a target state, it must provide a harness-owned read-back check. If the app calls an external API, its reset must be limited to that API's sandbox/test namespace; otherwise story tests may be read-only only.
   If the project supports an alternate startup mode, make it an invocable harness command—not a manual prose workaround—and record the actual selected mode in evidence.
   Between runs, never use a fixed sleep. After stopping a lifecycle, poll its harness-owned listeners/processes until released (harness-owned ports are free and the tracked process tree has exited), then require several consecutive health/read-back checks before the next test run. If either fails, recover the environment before recording evidence or a flakiness verdict.
   Exclude and rerun a run with fewer scenarios than expected. Investigate a broad unrelated failure wave using lifecycle and health logs before classifying it as flaky.
   Put one-time campaign-directory emptiness checks in the runner's once-per-run setup hook; config may declare paths but must not reject existing output because it can load more than once. Keep fixture state that must survive a worker restart or separate runner invocation in a declared, lifecycle-owned runtime directory—never the runner's disposable output/artifact directory.
3. Add Playwright, **Allure 3**, and the project-local Playwright MCP command. Install the `allure` package (not legacy `allure-commandline`) at the latest supported 3.x release with `--save-exact`, so the lockfile pins the reviewed CLI:
   ```powershell
   npm --prefix e2e install --save-dev --save-exact allure@3 allure-playwright
   ```
   Confirm `npx --no-install allure --version` reports `3.x` and `npx --no-install allure awesome --help` succeeds. Add runner configuration plus any approved auth/reset helpers. Final evidence must force video/trace through a campaign config or environment flag, never a per-spec convention. With `E2E_CAMPAIGN_SUITE`, require empty campaign-owned `ALLURE_RESULTS_DIR` and `PLAYWRIGHT_OUTPUT_DIR`, reject `--list`, and never copy shared results. A story spec must never call reset endpoints or hidden APIs itself.
4. Create a small foundation smoke test for login/navigation/reset. Run it once against the isolated lifecycle with the final evidence capture policy, then independently inspect the fresh passed result and its non-empty trace and video attachments.
5. Work from a Git repository: generate, review, and stage `e2e/e2e-foundation.json` over only those shared files. Verify it before every story with `--verify-foundation`.
6. Configure the host's three role bindings from its adapter, if available. For Claude Code, copy `<skill-root>/references/adapters/claude-code/story-e2e-discoverer.md`, `story-e2e-author.md`, and `story-e2e-executor.md` to `.claude/agents/`. A host without named roles must launch a fresh generic subagent with the role's rule file, `skill_root`, and story/handoff input. Confirm the discoverer and author can use the project-local Playwright browser tool once before the first story.

**Bootstrap gate:** do not begin a story campaign until the documented commands complete one clean start → smoke → teardown → restart cycle. A manual fallback is not a foundation capability.

**Foundation review:** before staging the baseline, perform one bounded review: (1) the harness never overwrites or deletes a pre-existing developer file; (2) the smoke invokes the documented harness reset/lifecycle command, not its private endpoint; (3) the manifest protects every shared harness file, including the smoke spec and dependency lockfile; and (4) teardown can target only resources created by that lifecycle. After any fix, repeat all four checks, then run and inspect one fresh smoke result.

The bootstrap owner owns these files. A story agent consumes them and reports drift; it does not repair them mid-campaign.

### Foundation-fix task

Given a `foundation-gap.json`: read the gap report and the app's own integration code, then make the **smallest** change that lets the harness reach the state the AC needs — a stand-in that matches the app's contract, or a scoped, reversible lever that fails a dependency on demand. If it is not small and mechanical, stop and hand back. Apply the Foundation review checks (1)–(4) to the change; if it touches a reusable fixture or helper, the fixture-commit gate also applies. Regenerate the manifest with `create_e2e_foundation_manifest.py`, stage the changed protected files so `--verify-foundation` has a consistent baseline (commit only if the user requested it), then hand back stating what you relied on (the app expects `X`; the harness now reaches `X`). A fresh agent that did not write the fix checks that against the app's real integration code and behaviour, then runs `--verify-foundation` and the isolated smoke before the story is re-delegated.

Foundation bootstrap is a separate trusted setup task: declare reusable runner configuration, authentication fixtures, helpers, and harness-owned stack bring-up scripts/definitions in a foundation manifest beside the harness, then stage the manifest and those files as the foundation baseline. A prerequisite outside the requested story outcome (for example local test auth) belongs here: test its enablement separately, then let feature stories consume a pre-verified fixture. If a story uncovers a missing one, stop before adding dependencies or building a per-story workaround; hand it off as a separate foundation-bootstrap task, then start a new campaign from preflight. Do not commit unless the user requests it. During a story run, never create, regenerate, stage, reset, stash, or edit the manifest or protected files; write only story specs, story-specific seed files, and campaign evidence. First verify the supplied baseline, and fail closed if it is missing or changed. Generate it generically during bootstrap only:

Before committing a new or changed reusable fixture/helper, the foundation setup must run one clean isolated smoke test and independently read the fresh runner result; imports, type checks, and a story-agent summary are insufficient. A worker-scoped fixture that creates durable state must prove restart safety by running setup twice in succession against the same isolated lifecycle, as a worker restart would: detect and reuse existing state rather than assuming a clean account, enrolment, or seed row. When a reusable persona can encounter optional startup dialogs, use a shared helper that clears them and re-checks the target state; never assume dialog order. A fixture-only auth helper may make a bounded retry for a documented transient provider fault and log it. On a documented fixture-auth transient, retry only after a known idempotent cleanup of the harness-owned authentication session; never reset shared or real provider state. Never use it when login itself is the story outcome.

```powershell
python "<skill-root>/scripts/create_e2e_foundation_manifest.py" e2e/e2e-foundation.json <runner-config> <reusable-fixture> <helper> <stack-script-or-definition>
```

Before discovery, run `python "<skill-root>/scripts/verify_allure_e2e.py" --verify-foundation e2e/e2e-foundation.json`; it reads the staged baseline and fails if the manifest or any protected file changes. Pass the same manifest to the final verifier. A story agent must not repair a failed foundation check—report the verifier's foundation-owner regenerate command. A story spec may import a relative helper only when it is declared in the staged foundation manifest; keep story-specific data inside the spec. The manifest is the trust boundary: forbidden setup/API patterns fail in story or unprotected code, while a protected helper may contain approved foundation setup; its imports are still checked for unprotected dependencies.

A final `SKIPPED` run still creates one Allure **skipped** scenario with the original hierarchy, parameters, and Test plan. Its title stays the verbatim `AC<n>:` criterion, never the skip reason; the reason goes in the Description's `Blocked: <why>` trailer plus a full `blocker evidence` text attachment, and the `test.skip` message stays short. Execute that scenario and pass the fresh-result verifier before handoff: an authored `test.skip` without an Allure result is not evidence. When an outcome depends on a prior action, establish that action in the same independent scenario before claiming the outcome is unavailable; a pre-trigger observation proves only the pre-trigger state. When the UI is reachable, capture the observed blocker before skipping; do not present source-only diagnosis as a test outcome. Do not invent replacement assertions.

### One-time common-journey bootstrap

Do this as a separate foundation-owner task, not during a story campaign. First scan the project's story specifications and make a short task list of repeated prerequisites. Promote a journey only when it appears in at least two distinct stories (or the project explicitly declares it foundational). Typical examples are role login, role landing page, and stable navigation to a work area; never promote a story's business outcome.

For each candidate, smoke it live with the reusable persona fixture and record it in `e2e/common-journeys.json`: lowercase `id`, short `purpose`, `personas`, 2+ visible `steps`, `smoke_spec`, and `reused_by`. Each `reused_by` entry names a story file and verbatim acceptance-criterion fragment; cite two distinct story files. Validate the inventory before review:

```json
{"version":1,"journeys":[{"id":"role-work-area","purpose":"signed-in role reaches its work area","personas":["Role A"],"steps":["sign in","open work area"],"smoke_spec":"foundation-tests/role-work-area.smoke.spec.ts","reused_by":[{"story":"001-a/spec.md","criteria":["Given a role is signed in"]},{"story":"002-b/spec.md","criteria":["When the role opens its work area"]}]}]}
```

```powershell
python "<skill-root>/scripts/verify_allure_e2e.py" `
  --verify-common-journeys e2e/common-journeys.json --stories-dir specs
```

Independently read the fresh smoke runner result before accepting the inventory, then protect the inventory and smoke spec in the foundation manifest. A later story may use a recorded journey for setup without re-discovering it, but must still validate its own user-visible AC outcomes live. Do not turn a common journey into a passing story assertion unless that journey is part of the requested story.

### Register Playwright MCP

Pin Playwright MCP in the project's test harness and launch that local binary without auto-installing. Avoid `@latest` in MCP startup: a registry lookup can outlive the client's connection window and leave tools missing for the session. For a harness that uses a **local self-signed IdP**, include `--ignore-https-errors` (local mock only — never against the public internet):

```powershell
npm --prefix e2e install --save-dev --save-exact @playwright/mcp
# Configure the project MCP command as:
npx --prefix e2e --no-install playwright-mcp --caps=devtools --ignore-https-errors
```

Install harness dependencies before starting the agent. If MCP startup fails, run the same local command with `--version`; do not repeatedly reconnect while an auto-install is still running.
