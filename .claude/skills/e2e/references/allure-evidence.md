# Allure Evidence Contract

Read this before authoring the final E2E spec. Use these APIs from
`allure-playwright`; Playwright `testInfo.annotations` does not populate these
Allure fields.

```ts
import { allure } from 'allure-playwright';

const campaignSuite = process.env.E2E_CAMPAIGN_SUITE;
if (!campaignSuite) throw new Error('E2E_CAMPAIGN_SUITE is required');
const STORY_INTENT = 'As a member, I want … so that …';
const AC_DESCRIPTION = 'AC1: Given … When … Then …';   // the one AC, verbatim from story-analysis.json
await allure.parentSuite(campaignSuite);                     // campaign
await allure.feature('Feature: Feature name');
await allure.suite('US<n>: short user-story title');
await allure.subSuite(AC_DESCRIPTION);                       // same verbatim AC line as the title
await allure.description(`${STORY_INTENT}\n\n${AC_DESCRIPTION}`); // user-story intent + this AC only
await allure.parameter('persona', 'active persona');
await allure.parameter('e2e_configuration', 'isolated E2E fixture');

await allure.attachment(
  'Test plan',
  ['Data:', '- Controlled persona + permitted oracle.',
   'Isolation:', '- Isolated E2E stack; harness-owned reset.',
   'Steps:',
   '1. Create the controlled state through the UI.',
   '2. Authenticate as the intended persona.',
   '3. Perform the story action and read the visible outcome.',
   'Visible outcomes:', '- <one human-visible result per mapped claim>',
   'Acceptance mapping:',
   '- AC1 | Story clause: <verbatim fragment> | Actor: <persona> | Visible outcome: <matching bullet>',
  ].join('\n'),
  'text/plain',
);
```

Attach `Test plan` before browser actions. `Steps:` is the user-level procedure —
3–6 numbered actions a reader can follow without opening Trace Viewer ("select
Medic, enter credentials, submit"). Never put selectors, private AI reasoning, or
assertion-by-assertion detail in it.

The subSuite (the scenario title) and the Description's AC line are **both** the
verbatim frozen `AC<n>:` criterion — for a passing, failing, or skipped scenario
alike. Description is `${STORY_INTENT}\n\n${AC_DESCRIPTION}` when the frozen story
has an `As … I want … so that …` intent; otherwise the `AC<n>:` line alone. The
verifier compares on a whitespace/quote-normalised form, so a compound multi-line
criterion is fine and a cosmetic edit to the story doc (a rewrap, a smart quote)
does not force a re-freeze — but a reworded or summarised criterion still fails.
An `AC<n>b` scenario keeps the suffix on the subSuite/test-name label only; its
Description AC line, mapping, and `Assertion:` tag use the base `AC<n>`.

A non-passing scenario appends **exactly one** plain-language reason paragraph
after the AC line, blank-line separated: `Failure: <why>` when the final status
is `failed`, `Blocked: <why>` when `skipped`. It is one short paragraph for a
non-technical reader — what the story required, what the product actually did,
and the most likely cause — required for that status and forbidden on a pass. It
never changes the title or the AC line, and never weakens the assertion or turns
a FAIL into a skip.

```ts
// only for a scenario you expect to end `failed` or `skipped`:
const REASON =
  'Failure: the story needs the In Transit view to show arrival + courier details, ' +
  'but the product renders only the status timeline. Likely: RequestDetail.tsx never ' +
  'renders the arrival/courier fields the backend already returns.';
await allure.description(`${STORY_INTENT}\n\n${AC_DESCRIPTION}\n\n${REASON}`);
```

Wrap every user-facing outcome assertion in a named Allure step. Capture the
observed value before the assertion; never derive it from implementation or
alter a generated report.

```ts
const expected = '/dashboard';
const observed = new URL(page.url()).pathname;
await allure.step(
  'Assertion: [AC1] actor: Member | dashboard opens after sign-in',
  async () => allure.step(
    `Evidence: expected: ${expected} | observed: ${observed}`,
    async () => expect(page).toHaveURL(new RegExp(`${expected}$`)),
  ),
);
```

Keep the `Assertion:` title to the plain-language user-visible outcome. Put runtime proof in one nested `Evidence: expected: … | observed: …` step, immediately around its matcher. Use response status only to synchronize or diagnose. An assertion must not use a bare HTTP status as its user-visible outcome. Represent an unavailable clause as a separate real skipped scenario with blocker evidence, never as a `SKIPPED:` or `BLOCKED:` step in a pass.

Use one exact parentSuite for the campaign and pass that same value as mandatory --campaign-suite. For readable reports, use `Feature: <name>`, `US<n>: <title>`, and the verbatim `AC<n>: <criterion>` as the scenario title. One Test plan/scenario may map multiple claims under the same AC, but never multiple ACs. Never use `STORY` or list every criterion. The frozen story-analysis and run manifest carry full-story identity. Persona, isolation, and stack details belong in parameters/Test plan, not the report hierarchy. Put environment switching in a harness-owned script or fixture outside a spec file; a browser test must not start, stop, or recreate services.


For a genuine blocked precondition, create one skipped scenario with the original
metadata and Test plan. The title stays the verbatim `AC<n>:` criterion — never
the skip reason. Put the reason in the Description's `Blocked: <why>` trailer and
the full detail in one `blocker evidence` attachment; keep the `test.skip` message
short. Do not invent substitute assertions.

```ts
const REASON = 'Blocked: no seeded persona is unassigned, so the placeholder branch cannot be reached without an out-of-story data step.';
await allure.description(`${STORY_INTENT}\n\n${AC_DESCRIPTION}\n\n${REASON}`);
await allure.attachment('blocker evidence', fullReason, 'text/plain');
test.skip(true, 'Persona is not exposed in an unassigned state.');
```

Run the skipped scenario and its fresh-result verifier before handoff. An authored `test.skip` without result JSON, report, and verifier success is not a verified skipped result.

## Test-plan contract

- Include concise `Data:`, `Isolation:`, `Steps:`, `Visible outcomes:`, and `Acceptance mapping:` sections. `Steps:` lists 3–6 numbered user-level actions (no selectors). `Assumptions` and semantic-review notes are useful when they explain a material judgment, but are not template fields.
- Map each outcome as
  `- AC1 | Story clause: <verbatim fragment> | Actor: <persona> | Visible outcome: <matching bullet>`.
  Prefix its runtime proof `Assertion: [AC1]`.
- The result verifier checks those structural links. The bounded review judges whether
  actor, action, polarity, quantity, named surface, and transition are genuinely proved.
- `Isolation` must name an isolated E2E stack/database before mutation of
  global or pre-existing state. Use harness-owned setup and seed thresholds directly.
- Prove outcomes in the live UI. Rendered labels/statuses need rendered-text proof;
  response codes, counts, disappearance, and related records are supporting evidence only.
- A reachable contradiction is FAIL. An authorised missing prerequisite is a separate
  skipped scenario with blocker evidence. Cite permitted test oracles in `Data`.

Before starting an isolated stack, run the preflight gate against the completed story spec and fix every finding. It checks the literal story binding, readable Test-plan/mapping structure, foundation integrity, and unsafe authoring shortcuts; the result verifier checks generated evidence. Neither replaces the bounded acceptance review of story meaning. That review must require an actual rendered value when the story asks to display one; a position/count/code/time label alone is insufficient.

```powershell
python "<skill-root>/scripts/verify_allure_e2e.py" --preflight <all-story-specs...> --story-analysis <story-analysis.json> --foundation-manifest e2e/e2e-foundation.json
```

After it passes, freeze the story analysis, spec, skill, verifier, and foundation until evidence is complete. A later change invalidates that evidence and requires a new preflight plus a fresh run.
Capture the final-run start immediately before Playwright. The verifier rejects
result JSON or report summary files older than it.

A trusted bootstrap task creates and stages a shared E2E foundation before story work. A story spec may import a relative helper only when it is declared in that staged manifest; keep story-specific data inside the spec. A story agent must only preflight and consume it—never regenerate, stage, or alter it. Do not commit unless requested.

```powershell
$campaign = (Resolve-Path '<campaign-directory>').Path
$env:E2E_CAMPAIGN_SUITE = '<campaign-id>'
$env:E2E_RETRIES = '0'
$env:ALLURE_RESULTS_DIR = "$campaign/allure-results"
$env:PLAYWRIGHT_OUTPUT_DIR = "$campaign/test-results"
# optional: $env:E2E_TIMEOUT_MS = '180000'
$runStarted = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
# Write and validate run-manifest.json now, including this timestamp plus the story-analysis and spec SHA-256 values.
# From the repository root, use the project's local E2E package.
# Do not auto-install either CLI.
# `npx --prefix e2e playwright test` (run from the repo root) has been observed to
# resolve playwright.config.ts's testDir/reporter correctly but silently return an
# EMPTY project.use ({}), dropping baseURL/ignoreHTTPSErrors — breaking every relative
# page.goto() and TLS handling with no error. Run it with cwd=e2e and no --prefix
# instead. <spec-or-suite> is now relative to e2e/ (e.g. tests/foo.spec.ts, not
# e2e/tests/foo.spec.ts); use an absolute path for <campaign> below since the cwd has changed.
Push-Location e2e
npx --no-install playwright test <spec-or-suite>
Pop-Location
npx --prefix e2e --no-install allure awesome "$campaign/allure-results" --output "$campaign/allure-report" --group-by feature,suite,subSuite
python "<skill-root>/scripts/verify_allure_e2e.py" "$campaign/allure-results" --report-dir "$campaign/allure-report" --expected-count <frozen-scenario-count> --spec-dir <spec-dir> --foundation-manifest <foundation-manifest> --campaign-suite $env:E2E_CAMPAIGN_SUITE --not-before $runStarted --run-manifest <run-manifest.json> --phase-timings "$campaign/phase-timings.json"
```

Start or reuse the stable report portal and use the URL it prints. Leave it
running so the developer can refresh and browse all completed campaigns:

```powershell
python "<skill-root>/scripts/serve_e2e_reports.py" start --artifacts-root artifacts/e2e --harness-root e2e
python "<skill-root>/scripts/serve_e2e_reports.py" verify --artifacts-root artifacts/e2e --harness-root e2e
```

The portal check verifies served HTML, JavaScript, and a real trace-attachment link automatically. Manually open Trace Viewer only for a `FAIL`/`SKIPPED` result or after a foundation/report-tool change.

The verifier reads full-story identity from the frozen run manifest, not Allure Description. When a story declares an `As …` intent, it requires that intent followed by its frozen `AC<n>:` criterion (compared whitespace/quote-normalised); otherwise it requires that AC line alone. Generate the report into a fresh campaign directory with Allure 3 `allure awesome --group-by feature,suite,subSuite`, so people see feature, user story, and the verbatim AC criterion (plus a `Failure:`/`Blocked:` reason line on a non-passing scenario); the internal run ID remains in result metadata. The combined portal makes a temporary display copy only; it never edits raw evidence, and it drops Playwright's auto-generated `error-context` attachment (a generic "Following Playwright test failed…" prompt) so the authored `blocker evidence` is the only failure narrative. Because the portal aggregates every campaign directory that holds results, a superseded run (a re-run story's discarded prior campaign) still shows its stale verdict there; when you re-run a story, delete or move aside the discarded campaign directory so the combined report reflects only authoritative runs. Never hand-write Allure v3 `widgets/statistic.json` or use legacy Allure v2 `widgets/summary.json`. If the output path is ambiguous, `cd` into the campaign parent first so it cannot collide with unrelated reports.

It must exit zero. If it fails, fix the test/report and rerun the suite. Do not start a Playwright evidence run until `--preflight` exits zero.
