---
name: browser-test
description: Builds and runs the application, launches agent-browser, and executes acceptance scenarios derived from the current GitHub issue's acceptance criteria. Use as a final acceptance gate after all bugs are fixed and before merging, to verify acceptance criteria are met in a live browser with screenshot evidence.
---

# Browser Acceptance Test

Run browser acceptance tests against a live app stack to verify the feature works end-to-end from the user's perspective. Derives scenarios from the GitHub issue's `## Acceptance criteria` section.

## Quick start

| Invocation | Behaviour |
|------------|-----------|
| `/browser-test` | All acceptance criteria, auto-detect URL |
| `/browser-test --scope=AC1` | Single acceptance criterion |
| `/browser-test --base-url=<url>` | Skip URL auto-detection |
| `/browser-test --skip-build` | Use already-running stack |

All options: `--scope=AC1|AC2|...|all`, `--base-url=<url>`, `--service=<compose-service>`, `--health-url=<url>`, `--startup-timeout=<seconds>` (default: 120), `--screenshots=true|false` (default: true), `--headless=true|false` (default: true), `--skip-build`, `--retain-stack`.

## Prerequisites

- **agent-browser** — must be installed: `npm i -g agent-browser && agent-browser install`. Before driving the browser, load its current command vocabulary at runtime with `agent-browser skills get core` (add `--full` for every command/flag) — this is the snapshot/ref loop, `click`/`fill`/`wait`, and semantic locators
- **GitHub issue** — fetch the current feature's issue via `gh issue view <number>` and extract the `## Acceptance criteria` section
- **Runtime** — prefer Docker Compose when a compose file exists; otherwise use the documented run command from `plan.md` or `README.md`

## Workflow

### 1. Fetch acceptance criteria

Resolve the issue number from the current branch name or ask the user. Fetch via `gh issue view <number>` and extract all `## Acceptance criteria` items as the test scenarios.

### 2. Build and start the app

```bash
docker compose up --build -d
```

If `--skip-build` is set, use `docker compose up -d`. If no compose file exists, use the documented run command from `plan.md` or `README.md`.

### 3. Determine base URL and wait for readiness

Resolve base URL from: `--base-url` if provided, then published compose port, then `plan.md`/`README.md`. Abort if none can be determined.

Poll `--health-url` if provided, else compose healthchecks, else `$BASE_URL` until a 2xx/3xx response or `--startup-timeout` expires. On timeout, capture `docker compose logs --tail 80` and abort.

### 4. Open browser and run scenarios

Load the agent-browser command vocabulary (`agent-browser skills get core` — the snapshot/ref loop, `click`/`fill`/`wait`, semantic locators), then open the app:

```bash
agent-browser open "$BASE_URL"
```

For each acceptance criterion in scope, drive the app with agent-browser:
1. Navigate to the relevant route using links, buttons, or menus visible in the app
2. Snapshot interactive elements (`agent-browser snapshot -i`) and act on the `@eN` refs — re-snapshot after every page change
3. Verify the expected visible text, controls, data state, or interaction result
4. Capture a screenshot if `--screenshots=true`

Status per criterion: **PASS** (expected behaviour observed), **FAIL** (reachable but behaviour absent or wrong), **SKIP** (unavailable test data or optional precondition missing), **BLOCKED** (crash, auth wall, or unrecoverable state).

See [references/edge-cases.md](./references/edge-cases.md) for edge cases to probe per scenario type.

### 5. Write report and clean up

Write the report to `artifacts/browser-test/{feature}-browsertest.md`. See [references/report-template.md](./references/report-template.md) for the full template.

Unless `--retain-stack` is set:

```bash
docker compose down
```

## Rules

- Derive scenarios from the issue's `## Acceptance criteria` only — do not invent scenarios.
- Do not assume domain routes, component libraries, or API endpoints unless visible in the app or stated in the issue.
- Mark SKIP rather than FAIL when a precondition is missing but optional.
- Mark BLOCKED rather than FAIL when the app is unreachable or unrecoverable.
- Evidence must come from browser snapshots or screenshots, not from code inspection.

See [references/troubleshooting.md](./references/troubleshooting.md) for common failure causes and fixes.
