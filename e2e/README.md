# Playwright E2E foundation

- Install: `D:\projects\builder-day\node\npm.cmd ci --prefix e2e --ignore-scripts`
- Run the smoke test: `D:\projects\builder-day\node\npm.cmd --prefix e2e run test:smoke`
- Base URL: `https://127.0.0.1:8443` (override with `E2E_BASE_URL`). The backend's self-signed certificate is accepted.
- Lifecycle: the harness starts the backend with the `local` profile, waits for `GET /csrf` (up to 5 minutes, since `spring-boot:run` first rebuilds the frontend; override with `E2E_STARTUP_TIMEOUT_MS`, and it fails fast if Maven exits), records its unique lifecycle ID in `artifacts/e2e/runtime/`, and tears down only that process tree.
- Test account: `johndoe` / `Password123!`, seeded only by the `local` profile's changelog (`backend/local/`), so it never exists in dev, sit or prod. Only scenarios about the seed itself sign in as it; the rest take the `account` fixture (`fixtures/session.ts`), which registers a fresh user per test, since one session per user means parallel logins expire each other.
- Admin test account: `e2eadmin` / `Str0ng!Passw0rd-e2e` (override with `E2E_ADMIN_USERNAME` / `E2E_ADMIN_PASSWORD`), created by Admin bootstrap on the harness backend from `ADMIN_USERNAME` / `ADMIN_PASSWORD` (set only for the spawned process in `scripts/lifecycle.mjs`). It exists only in harness-started runs and never in a deployed environment; no backend configuration file carries these credentials. Scenarios that sign in as it use the shared serial `seed-user` project, like `johndoe`.
- Source address: the fixture gives each test its own `X-Forwarded-For` address, so per-IP limits (login rate limit, registration lock) don't leak between parallel tests.
- Evidence: `artifacts/e2e/test-results/` and `artifacts/e2e/allure-results/`.

The test lifecycle starts a fresh in-memory H2 local database, so it is the isolation/reset boundary. E2E specs must not call private reset endpoints.
