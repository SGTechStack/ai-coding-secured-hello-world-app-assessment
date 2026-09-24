# E2E harness — Hello World Auth

Trustworthy Playwright + Allure 3 evidence for the SPA (`:3000`) + Spring Boot
API (`:8080`). This directory is the **foundation baseline**: a story campaign
consumes it and must not modify any file protected by `e2e-foundation.json`.

## Project profile

| Fact | Value |
| --- | --- |
| Install (harness) | `npm --prefix e2e ci` (or `npm --prefix e2e install`) |
| Install (app, once) | `cd frontend && npm ci` — backend builds via Maven |
| App start (manual) | `.\start.ps1` from repo root (backend dev :8080 + Vite :3000) |
| Harness lifecycle | `powershell.exe -ExecutionPolicy Bypass -File e2e\scripts\lifecycle.ps1 up` / `down` (see below) |
| Health check | `GET http://localhost:8080/actuator/health` → `{"status":"UP"}` (permit-all) |
| Browser base URL | `http://localhost:3000` (Vite dev server) |
| API base URL | `http://localhost:8080` |
| Test admin | `admin` / `admin-local-dev-password` (dev profile seed only) |
| Test env | Local dev profile: in-memory H2 (`create-drop`), Spring Session JDBC |
| Report artifacts | `artifacts/e2e/` (git-ignored, generated) |
| Runnable specs | `e2e/tests/` |
| Reusable fixtures | `e2e/fixtures/` |
| Harness scripts | `e2e/scripts/` |

## Auth fixture

`e2e/fixtures/auth.ts` exposes reusable Playwright fixtures:

- `loginAs(page, username, password)` — drives the real `/login` form and waits
  for the protected landing page. No API calls, no token injection.
- `adminCredentials` — the dev-seed admin persona (`admin` /
  `admin-local-dev-password`).

A story spec consumes these through the foundation manifest; it must never call
reset endpoints or hidden APIs itself.

## Reset / isolation contract

The dev backend uses an **in-memory H2 database with `ddl-auto: create-drop`**.
There is **no HTTP data-reset endpoint**; the only reset primitive the app
offers is a **backend process restart**, which drops and recreates the schema
and re-runs the `AdminSeeder`. The harness therefore models isolation as a
**uniquely identified backend lifecycle**:

- `lifecycle.ps1 up -LifecycleId <id> -BackendPort <p> -FrontendPort <q>`
  starts a backend (dev profile) and Vite dev server bound to
  harness-owned ports, prints the lifecycle id + base URLs, polls
  `/actuator/health` until several consecutive `UP` responses, and records
  its tracked PIDs to `artifacts/e2e/lifecycle/<id>.json`.
- `lifecycle.ps1 down -LifecycleId <id>` kills **only** the tracked process
  tree for that lifecycle, then polls until the harness-owned ports are free
  and the tracked PIDs have exited. It never touches a process it did not
  start.
- `lifecycle.ps1 reset -LifecycleId <id>` is `down` followed by `up` — a full
  data reset via schema recreation, the app's only reset primitive.

State-changing commands exit non-zero on real failure and distinguish that
from benign diagnostics; every "declared state" (`UP`, port free) has a
harness-owned read-back poll. Between runs the harness **never sleeps a fixed
interval** — it polls listeners/PIDs until released, then requires consecutive
health checks before the next run.

Because the app has no test-only data-reset API and no external API, story
tests that mutate durable state must reset by restarting the lifecycle; there
is no sandbox namespace to scope a partial reset to.

### Running under WSL

Everything executes on the **Windows side** — node/npm are Windows binaries
(`/mnt/c/chris/tools/nodejs`), `powershell.exe` (Windows PowerShell 5.1) is the
only PowerShell host, and Playwright drives the Windows Chromium cache. WSL's
localhost forwarding does NOT reach the app listeners, so never run the specs
from Linux bash. Invoke from WSL like:

```bash
# `up` spawns detached children that inherit stdout — redirect to a log so the
# invoking shell returns instead of staying open.
powershell.exe -NoProfile -ExecutionPolicy Bypass \
  -File 'C:\chris\dev\temp\assessment\e2e\scripts\lifecycle.ps1' \
  up -LifecycleId <id> -BackendPort 18080 -FrontendPort 13000 -SkipBuild \
  > artifacts/e2e/lifecycle/<id>-up.log 2>&1

# Specs (finite run, safe to run in the foreground):
powershell.exe -NoProfile -Command "Set-Location 'C:\chris\dev\temp\assessment\e2e'; \
  \$env:E2E_BASE_URL='http://localhost:13000'; \
  \$env:ALLURE_RESULTS_DIR='C:\...\artifacts\e2e\<dir>\allure-results'; \
  \$env:PLAYWRIGHT_OUTPUT_DIR='C:\...\artifacts\e2e\<dir>\test-results'; \
  npx --no-install playwright test <spec>"
```

If a previous session left a stale `artifacts/e2e/lifecycle/<id>.json`, check
the recorded ports/PIDs on the Windows side (`Get-NetTCPConnection`,
`Get-Process`) before reusing them — PIDs can be recycled.

## Video / trace policy

`playwright.config.ts` forces `video: 'on'` and `trace: 'on'` for every run —
never a per-spec convention. Final campaign evidence flows through the
`E2E_CAMPAIGN_SUITE` env contract (empty campaign-owned `ALLURE_RESULTS_DIR`
and `PLAYWRIGHT_OUTPUT_DIR`).

## Playwright MCP

Pinned locally (`@playwright/mcp`, `--save-exact`). Launch without
auto-install:

```powershell
npx --prefix e2e --no-install playwright-mcp --caps=devtools
```

(The app runs over plain HTTP locally; `--ignore-https-errors` is **not** used
because there is no self-signed IdP.)
