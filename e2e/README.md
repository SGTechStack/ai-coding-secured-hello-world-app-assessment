# E2E Harness — Secured Hello World Auth App

Playwright + Allure 3 end-to-end evidence harness for the Secured Hello World
app (React frontend + Spring Boot backend). This directory is the **foundation**:
shared runner config, lifecycle scripts, and a foundation smoke test. Story
campaigns live under `../artifacts/e2e/campaigns/`; runnable story specs live in
`e2e/tests/`.

## Project profile

| Aspect | Value |
| --- | --- |
| Frontend (browser base URL) | `http://localhost:5173` (Vite, `strictPort`) |
| Backend API origin | `http://localhost:8080` (Spring Boot, dev profile) |
| Backend health (unauthenticated) | `GET http://localhost:8080/api/health` → `{ "status": "UP", "time": <ISO> }` |
| Persistence | H2 in-memory (`jdbc:h2:mem:securedhello`), reset by a fresh backend start |
| Session | server-side, `SESSION` cookie; CSRF via readable `XSRF-TOKEN` cookie |
| Seeded admin | `app.admin.username` / `app.admin.password` (defaults `admin` / `change-me-admin-pw`) |
| Designated test environment | local dev profile only, H2 in-memory, HTTP (never a shared/prod DB) |

## Install

Run once from the repository root:

```powershell
# Backend + frontend are built/run by their own toolchains (Java 21 + Maven 3.9, Node 22).
npm --prefix frontend install
npm --prefix e2e install
npx --prefix e2e --no-install playwright install chromium
```

The e2e harness is its own npm project (`e2e/package.json`) with its own
`node_modules`, Playwright browsers, and Allure 3 CLI pinned with `--save-exact`.

## Start / lifecycle (harness-owned)

The harness owns the whole stack lifecycle. Do not start the servers by hand for
an evidence run — use the lifecycle command so the base URL and lifecycle ID are
recorded and teardown is scoped to exactly what was started.

```powershell
# Bring up backend (:8080) + frontend (:5173), print base URL + lifecycle id,
# and block until both pass health/read-back checks.
node e2e/scripts/lifecycle.mjs up

# Reset only this lifecycle's designated test data (fresh in-memory H2 + re-seeded
# admin) via a full backend restart, then re-verify health.
node e2e/scripts/lifecycle.mjs reset

# Tear down ONLY the processes/ports this lifecycle started, then poll until the
# harness-owned ports are free and the tracked process tree has exited.
node e2e/scripts/lifecycle.mjs down
```

`lifecycle.mjs` writes its state (lifecycle id, PIDs, base URLs) to
`e2e/.runtime/lifecycle.json` — a declared, lifecycle-owned runtime directory
that survives a runner restart. It never touches the runner's disposable
output/artifact directory.

### Reset / isolation contract

- **Unique lifecycle id** — every `up` mints a UTC-stamped id, printed to stdout
  and recorded in `e2e/.runtime/lifecycle.json`.
- **Scoped reset** — `reset` restarts only the backend this lifecycle started,
  which discards the in-memory H2 database and re-runs admin bootstrap. It never
  reaches an external API or a shared database. The app calls no external API,
  so reset is limited to this local in-memory instance.
- **Read-back** — after `up` and `reset`, the harness polls `GET /api/health`
  and the frontend root, requiring several consecutive successes (no fixed
  sleeps) before returning success. A declared target state always has a
  harness-owned read-back check; a non-zero exit means a real failure, not a
  benign diagnostic.
- **Scoped teardown** — `down` kills only the PIDs recorded for this lifecycle
  and waits until its ports (`5173`, `8080`) are released and the tracked
  process tree has exited.

## Test accounts / auth fixture

- Seeded admin: username `admin`, password `change-me-admin-pw` (dev defaults;
  overridable via `APP_ADMIN_USERNAME` / `APP_ADMIN_PASSWORD`).
- Regular users are created per test via the app's own registration flow; the
  in-memory database is discarded on `reset` / `down`, so no cleanup is needed.
- The frontend has full register/login/forgot-password/reset-password/hello/admin
  pages. A reusable persona/auth fixture is **not** pre-built here yet; add one
  under `e2e/fixtures/` as a foundation-bootstrap task once it's needed by two or
  more story campaigns, rather than duplicating login flows per spec.

## Reports / directories

| Directory | Purpose | Tracked? |
| --- | --- | --- |
| `e2e/tests/` | runnable specs (foundation smoke + story specs) | yes |
| `e2e/fixtures/` | reusable, foundation-protected fixtures/helpers | yes |
| `e2e/scripts/` | harness lifecycle + reset scripts | yes |
| `e2e/.runtime/` | lifecycle state (PIDs, ids) — regenerated | no (gitignored) |
| `e2e/allure-results/`, `e2e/test-results/`, `e2e/playwright-report/` | default local runner output | no (gitignored) |
| `../artifacts/e2e/campaigns/<utc-id>-<slug>/` | per-campaign frozen evidence (results, report, handoffs, timings) | no (gitignored, generated) |

## Foundation smoke

`e2e/tests/foundation.smoke.spec.ts` drives a browser to the frontend, verifies
backend connectivity is rendered (navigation + health), and exercises the
harness reset contract. Run it against a live lifecycle:

```powershell
node e2e/scripts/lifecycle.mjs up
npx --prefix e2e --no-install playwright test --config e2e/playwright.config.ts
node e2e/scripts/lifecycle.mjs down
```

Final evidence (video + trace) is forced by the campaign runner config, never by
a per-spec convention.
