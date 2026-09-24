# Demo App

Session-based login: a Spring Boot API in `backend/` and a React SPA in `frontend/`.
Spec and tickets live in `.scratch/simple-login/`.

## Prerequisites

- JDK 21 and Maven 3.9+
- Node 24+ and npm

## Run locally

Start the backend (port 8080, in-memory H2 migrated by Flyway). `mvn spring-boot:run` uses the
`dev` profile, which seeds the demo account and lets the session cookie work over plain HTTP:

```sh
cd backend
mvn spring-boot:run
```

In a second terminal, start the frontend (port 3000). The SPA calls the API directly on its own
origin, `http://localhost:8080`, with credentials; there is no dev proxy:

```sh
cd frontend
npm install
npm run dev
```

Open <http://localhost:3000/login> and sign in with the demo account:
username `johndoe`, password `Password123!`.

| Setting                    | Default                 | Where                                                                                               |
| -------------------------- | ----------------------- | --------------------------------------------------------------------------------------------------- |
| Frontend port              | `3000`                  | `FRONTEND_PORT` when starting `npm run dev` or `npm run preview`                                    |
| Backend port               | `8080`                  | `SERVER_PORT` (Spring Boot)                                                                         |
| API base URL               | `http://localhost:8080` | `VITE_API_BASE_URL`, read at build time (`npm run dev`/`npm run build`); also the CSP `connect-src` |
| CORS allowed origins (API) | `http://localhost:3000` | `APP_CORS_ALLOWED_ORIGINS` (`app.cors.allowed-origins`), comma-separated                            |

When you move either port, keep the other side in step, e.g.
`SERVER_PORT=18080 APP_CORS_ALLOWED_ORIGINS=http://localhost:13000 mvn spring-boot:run` and
`FRONTEND_PORT=13000 VITE_API_BASE_URL=http://localhost:18080 npm run dev`.

The API only answers cross-origin requests from the exact origins in `app.cors.allowed-origins`
(no wildcards or `null`). The `dev` and `test` profiles default it to `http://localhost:3000`;
production has no default, and the API refuses to start without it. The allow-list is also what
keeps the CSRF token returned by `GET /api/v1/auth/csrf` away from other sites. The SPA and the API
must share a registrable domain (e.g. `app.example.com` and `api.example.com`), because the
session cookie is `SameSite=Strict`.

The database is in memory, so it is recreated on every backend restart.

Outside the `dev` profile the demo account is not seeded and the session cookie is `Secure`
(`HttpOnly`, `SameSite=Strict`), so it only works over HTTPS. The `prod` profile also redirects plain
HTTP to HTTPS: `mvn spring-boot:run -Dspring-boot.run.profiles=prod`, or
`SPRING_PROFILES_ACTIVE=prod APP_CORS_ALLOWED_ORIGINS=https://app.example.com java -jar target/demo_app-0.0.1-SNAPSHOT.jar`.

## Serving the production build

`npm run build` writes the SPA to `frontend/dist/`, with `VITE_API_BASE_URL` baked in. Whatever
host serves it must send these headers on every response (at least on `index.html`).
`npm run preview` serves the build with exactly these headers on `FRONTEND_PORT` (default `3000`).
They are defined once, in `frontend/vite.config.ts`:

| Header                    | Value                                      |
| ------------------------- | ------------------------------------------ |
| `Content-Security-Policy` | see below                                  |
| `Referrer-Policy`         | `no-referrer`                              |
| `X-Content-Type-Options`  | `nosniff`                                  |
| `Permissions-Policy`      | `camera=(), geolocation=(), microphone=()` |
| `X-Frame-Options`         | `DENY`                                     |

```text
default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self';
connect-src 'self' <API origin>; object-src 'none'; base-uri 'none'; form-action 'self';
frame-ancestors 'none'; require-trusted-types-for 'script'
```

(one line in the header; `<API origin>` is the origin of `VITE_API_BASE_URL`). No directive is
relaxed: the app and its dependencies need no inline script, inline `<style>` or Trusted Types
exemption. `index.html` has no inline script: the pre-paint theme script is
`frontend/public/theme-init.js`, loaded as a blocking `<script src>` in `<head>`. The Vite dev
server (`npm run dev`) sends none of these headers, because React Refresh injects an inline
script, so CSP problems only show up on the production build. That is why the e2e suite runs
against `vite preview`.

## Tests and quality gates

Backend: MockMvc integration tests plus a JaCoCo check (at least 80% instruction coverage).

```sh
cd backend
mvn verify
```

Frontend: formatting, type check, tests with coverage (at least 80%, thresholds in
`vitest.config.ts`), and production build.

```sh
cd frontend
npx prettier --check .
npx tsc --noEmit
npx vitest run --coverage
npx vite build
```

`npm run check` runs all four in order. Use `npm test` for watch mode and `npm run format` to fix
formatting.

### Dependency vulnerability scans

Run both before merging. Neither is part of `mvn verify` or `npm run check`, because both need
network access to vulnerability databases.

Frontend: fails on any high or critical advisory in the locked dependency tree.

```sh
cd frontend
npm audit --audit-level=high
```

Backend: the OWASP dependency-check Maven plugin (the repo's `dependency-vuln-scan` gate), run as
a fully qualified goal, so it is not declared in `pom.xml`. The first run downloads the NVD data
and can take a long time. Add `-DnvdApiKey=<key>` to speed that up. The report is written to
`target/dependency-check-report.json`.

```sh
cd backend
mvn org.owasp:dependency-check-maven:check -DfailBuildOnCVSS=7 -Dformat=JSON
```

## End-to-end acceptance suite

A Playwright suite in `frontend/e2e/` runs the SPA's production build against the real API. It has one test per
Gherkin scenario in the spec: Story 1, Scenarios 1–8, and Story 2, Scenarios 1–4. Story 3 (Logout),
Scenarios 1–6, comes from the logout spec's acceptance scenarios. The tests sign in with the seeded
`johndoe` account. Server failures (login and logout 5xx or network errors) are simulated with
Playwright request interception. Playwright's `webServer` starts the backend in the `dev` profile
(`mvn spring-boot:run -Dspring-boot.run.profiles=dev`, port 8080), builds the SPA and serves it
with `vite preview` (port 3000), with the production CSP and headers above, and stops both when
the run ends. The SPA calls the API cross-origin, as in production. `e2e/spa-hardening.spec.ts`
checks the headers and that injected script is blocked.

Every spec imports `test` and `expect` from `e2e/fixtures.ts`, never from `@playwright/test`. That
fixture fails a test on any `securitypolicyviolation` event or CSP/Trusted Types console error in
its browser context. A test that causes a violation on purpose sets
`test.use({ failOnCspViolation: false })` and asserts on the `cspViolations` fixture instead.

```sh
cd frontend
npx playwright install chromium   # once, to download the browser
npm run e2e
```

Both ports can be overridden with environment variables, for example to run the suite beside dev
servers already on the defaults:

| Variable        | Default | Used by                                                                                                                        |
| --------------- | ------- | ------------------------------------------------------------------------------------------------------------------------------ |
| `BACKEND_PORT`  | `8080`  | Playwright: Spring Boot's `SERVER_PORT`, and the SPA's `VITE_API_BASE_URL` (so the CSP `connect-src`)                          |
| `FRONTEND_PORT` | `3000`  | Playwright: the `vite preview` port, `baseURL`, and the API's `APP_CORS_ALLOWED_ORIGINS`; also `npm run dev`/`npm run preview` |

```sh
BACKEND_PORT=18080 FRONTEND_PORT=13000 npm run e2e
```

On a developer machine, servers already listening on those ports are reused, with whatever CORS
origin and API URL they were started with. With `CI` set, the suite always starts its own servers.
Use `npx playwright test --ui` to debug. On failure, traces go to
`frontend/test-results/`; open one with `npx playwright show-trace <path>`. The suite is separate
from `npm run check` (Vitest only picks up `src/**/*.test.{ts,tsx}`), but `tsc` and Prettier also
check the specs.
