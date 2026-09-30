# E2E acceptance suite (Gherkin + Playwright)

Black-box acceptance tests for [`prd/assessment-prd.md`](../prd/assessment-prd.md), written in Gherkin and run by
[playwright-bdd](https://vitalets.github.io/playwright-bdd/) on Playwright. Every acceptance criterion in Stories 1–12 and every
Non-Functional & Security requirement maps to at least one scenario.

## Run

Prerequisites: Node 22+, Java 21+, Maven. The backend must run with the `dev` profile (in-memory H2, H2 console, non-Secure
cookie over HTTP).

```bash
cd e2e
npm ci
npx playwright install chromium

npm test              # starts backend + frontend if they aren't running, then runs everything
npm run test:green    # excludes @prd-gap and @known-defect (use this as the CI gate)
npm run test:ui-only  # only the browser-driven scenarios
npx playwright test --grep @story-7   # one story
npm run report        # open the HTML report
```

If the apps are already running, set `E2E_SKIP_WEBSERVER=1`. Everything else in `support/env.ts` can be overridden
through `E2E_*` variables (URLs, admin credentials, lockout policy, H2 JDBC URL, log file path).

## How it works

- `features/*.feature` hold the scenarios, one file per story plus `13-security-nfr.feature`. `bddgen` compiles them into
  `.features-gen/` (git-ignored).
- `steps/` has the step definitions: `api.steps.ts` (HTTP, sessions, cookies), `state.steps.ts` (DB state, logs, reset
  tokens), `admin-and-security.steps.ts` (admin list, bootstrap, fixation, CORS) and `ui.steps.ts` (browser, using
  role/label locators).
- Scenarios are isolated and run in parallel. Each alias (`"alice"`) maps to a freshly registered, uniquely named account.
  Admin scenarios register their own admin, which the bootstrap admin promotes.
- `support/global-setup.ts` logs in with the configured `app.admin.*` credentials, records the Story 12 evidence, and then
  changes the password (the app forces this on first login) to `E2E_ADMIN_PASSWORD`. Later runs against the same backend
  process use the changed password.
- `support/h2-probe.ts` reads the in-memory H2 database through the dev-only `/h2-console`. It is the only black-box way to
  check facts the API never exposes (BCrypt hashes, `failed_login_attempts`, `locked_until`, reset-token hashes), and to
  fast-forward time by expiring a lockout or token instead of waiting 15–30 minutes.
- `support/backend-log.ts` reads `backend/logs/spring.log` (ECS JSON) and only looks at the lines each scenario wrote. It
  backs the audit-log and "never log passwords/tokens" checks.
- Reset tokens: the stub `EmailService` keeps the reset link in memory only and never logs or exposes it, so the token
  secret can't be recovered from outside. The suite runs the real request flow, keeps the token row and selector the app
  issued, and replaces only the BCrypt verifier hash with one it knows. The backend then validates that token like a mailed
  one.

## Current results

Against the current build: 73 passed, 2 skipped, 3 failed. The 3 failures are app issues, tagged so they stay visible. The
green set (`npm run test:green`) passes 73/73 with 2 skipped.

| Tag | Scenario | Finding |
| --- | --- | --- |
| `@prd-gap` | Story 3: IP throttling across usernames | Not implemented. [ADR](../docs/adr/no-ip-rate-limiting-waf-delegated.md) hands it to a WAF, which conflicts with PRD Story 3 AC 3. |
| `@known-defect` | Story 4: logout clears the session cookie | `POST /api/logout` invalidates the session but sends no `Set-Cookie` that expires `JSESSIONID`. Replaying the old cookie is correctly rejected (401). |
| `@known-defect` | Story 10: unknown role value | `PATCH …/role` with `{"role":"SUPERUSER"}` returns 500. `Role.valueOf` throws `IllegalArgumentException`, which lands in the catch-all handler. It should be a 400. |

Skipped (`@skip`), with the reason in the feature file:

- Story 12, "no duplicate on restart": restarting the dev backend wipes the in-memory DB. `AdminBootstrapIntegrationTest` covers this.
- NFR, "Secure cookie": the dev profile turns `Secure` off because it runs over HTTP, as the PRD accepts.

## Traceability

| PRD item | Feature file |
| --- | --- |
| Story 1 Registration | `01-registration.feature` |
| Story 2 Login | `02-login.feature` |
| Story 3 Lockout / IP throttling | `03-lockout-and-throttling.feature` |
| Story 4 Logout | `04-logout.feature` |
| Story 5 Hello | `05-hello.feature` |
| Story 6 Reset request | `06-password-reset-request.feature` |
| Story 7 Reset confirm | `07-password-reset-confirm.feature` |
| Stories 8–11 Admin | `08`–`11-admin-*.feature` |
| Story 12 Bootstrap | `12-admin-bootstrap.feature` |
| NFRs: role enforcement, session security, CSRF, CORS, enumeration resistance, audit logging | `13-security-nfr.feature` (enumeration also in 02/06) |

Filter by `@story-N`, `@nfr`, `@ui`, `@admin`, `@password-reset`, etc.
