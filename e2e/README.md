# E2E harness

Playwright browser tests for the Hello Auth app, with Allure 3 reports. Runnable specs live in
`tests/`; generated evidence goes to `../artifacts/e2e/` (not committed).

## Project profile

| | |
|---|---|
| **Install** | `npm --prefix frontend ci && npm --prefix e2e ci && npm --prefix e2e run browsers:install`. Linux/WSL also needs Chromium's system libraries once: `sudo npx playwright install-deps chromium`, or the equivalent `apt-get install` list. |
| **Run everything** | `npm --prefix e2e test`: starts a fresh lifecycle, runs the specs, stops it. Extra args go to `playwright test`. |
| **Smoke** | `npm --prefix e2e run test:smoke` |
| **Start / stop by hand** | `npm --prefix e2e run lifecycle:start`, then `npx --prefix e2e playwright test`, then `npm --prefix e2e run lifecycle:stop` |
| **Health check** | `npm --prefix e2e run lifecycle:status`. `start` only returns after 3 consecutive healthy checks of `GET /actuator/health` (status `UP`) and the SPA's `index.html`. |
| **Browser base URL** | `http://localhost:13000` (SPA, production build). The API is at `http://localhost:18080`. Override the ports with `E2E_WEB_PORT` / `E2E_API_PORT`. |
| **Test accounts** | `admin` fixture: the ADMIN seeded by each lifecycle (`e2e_admin`, random password in `.runtime/<id>/state.json`). `newUser()` fixture: a fresh USER created through the public registration API. |
| **Email** | The dev profile's stub EmailService logs reset links; `fixtures/mailbox.ts` reads them from the lifecycle's log. |
| **Test environment** | Local only: a throwaway stack per lifecycle. It never uses the developer servers on 8080/3000 or `backend/data/`. |
| **Reports** | `test` / `test:smoke`: `../artifacts/e2e/runs/<lifecycle-id>/{allure-results,test-results}`. Campaign runs use campaign-owned directories under `../artifacts/e2e/campaigns/`. |

## Reset and isolation contract

- **Start:** `start` creates a uniquely identified lifecycle (`e2e-<utc>-<hex>`) under `.runtime/<id>/`, containing:
  - its own H2 database and logs
  - a built SPA
  - a backend jar built from a copy of the sources (never written to `backend/target`)
  - a generated admin password

  It prints `E2E_LIFECYCLE_ID`, `E2E_BASE_URL` and `E2E_API_URL`. It refuses to start if a lifecycle is still running, or if either port is in use by anything else.
- **Reset:** `reset` stops the current lifecycle and starts a fresh one. This is the only reset, and it discards all test data along with in-memory state such as the per-IP login throttle. Specs never call reset endpoints or private APIs; they create their own uniquely named users.
- **Stop:** `stop` signals only the process groups recorded at start. It then polls until those processes have exited and both ports are free, and fails if they are not released within 30 s. It deletes only its own `.runtime/<id>/` (`--keep` preserves it for debugging). `run` keeps it automatically when tests fail.
- **Exit codes:** every command exits 0 on success and non-zero on a real failure. Build warnings are not failures.
- **Shared state:** the per-IP login throttle (20 failures per 15 minutes) and account lockout are real and shared within one lifecycle, and all browser traffic comes from 127.0.0.1. Specs that deliberately exhaust the throttle need their own lifecycle.
- **Startup modes:** only one exists (`standard`); it is recorded in the Allure environment info.

## Evidence policy

`E2E_CAMPAIGN_SUITE` (campaign runs) or `E2E_EVIDENCE=final` makes the config record video and trace for every test. Campaign runs must set empty, campaign-owned `ALLURE_RESULTS_DIR` and `PLAYWRIGHT_OUTPUT_DIR`, and may not use `--list`.

## Browser tool for agents

The Story-E2E role agents (`.claude/agents/story-e2e-*.md`) launch the pinned local Playwright MCP:
`npx --prefix e2e --no-install playwright-mcp --caps=devtools --browser=chromium --headless`.
