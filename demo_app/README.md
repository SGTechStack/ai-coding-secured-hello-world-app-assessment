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

In a second terminal, start the frontend (port 5173; `/api` is proxied to port 8080, or to
`BACKEND_PORT` when set):

```sh
cd frontend
npm install
npm run dev
```

Open <http://localhost:5173/login> and sign in with the demo account:
username `johndoe`, password `Password123!`.

The database is in memory, so it is recreated on every backend restart.

Outside the `dev` profile the demo account is not seeded and the session cookie is `Secure`
(`HttpOnly`, `SameSite=Strict`), so it only works over HTTPS. The `prod` profile also redirects plain
HTTP to HTTPS: `mvn spring-boot:run -Dspring-boot.run.profiles=prod`, or
`SPRING_PROFILES_ACTIVE=prod java -jar target/demo_app-0.0.1-SNAPSHOT.jar`.

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

A Playwright suite in `frontend/e2e/` runs the real SPA against the real API. It has one test per
Gherkin scenario in the spec: Story 1, Scenarios 1–8, and Story 2, Scenarios 1–4. Story 3 (Logout),
Scenarios 1–6, comes from the logout spec's acceptance scenarios. The tests sign in with the seeded
`johndoe` account. Server failures (login and logout 5xx or network errors) are simulated with
Playwright request interception. Playwright's `webServer` starts the backend in the `dev` profile
(`mvn spring-boot:run -Dspring-boot.run.profiles=dev`, port 8080) and the Vite dev server (port 5173) and stops them when the run
ends.

```sh
cd frontend
npx playwright install chromium   # once, to download the browser
npm run e2e
```

Both ports can be overridden with environment variables, for example to run the suite beside dev
servers already on the defaults:

| Variable        | Default | Used by                                                                              |
| --------------- | ------- | ------------------------------------------------------------------------------------ |
| `BACKEND_PORT`  | `8080`  | Playwright (passed to Spring Boot as `SERVER_PORT`) and the Vite `/api` proxy target |
| `FRONTEND_PORT` | `5173`  | Playwright (Vite dev server port and `baseURL`)                                      |

```sh
BACKEND_PORT=18080 FRONTEND_PORT=15173 npm run e2e
```

On a developer machine, servers already listening on those ports are reused. With `CI` set, the
suite always starts its own servers. Use `npx playwright test --ui` to debug. On failure, traces go to
`frontend/test-results/`; open one with `npx playwright show-trace <path>`. The suite is separate
from `npm run check` (Vitest only picks up `src/**/*.test.{ts,tsx}`), but `tsc` and Prettier also
check the specs.
