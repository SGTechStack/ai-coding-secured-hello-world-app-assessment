import { defineConfig, devices } from "@playwright/test";

/**
 * Browser tests for the frontend.
 *
 * These are *not* the required test seam. The PRD mandates integration tests for the
 * security-critical paths and leaves UI coverage to discretion, and the mandated coverage lives in
 * `api/src/test` at the HTTP boundary — lockout counters, replayed cookies and single-use tokens are
 * server-side state that a browser cannot inspect.
 *
 * What these add is the one thing those cannot: evidence that the React application actually works.
 * A backend can pass every test it has while the frontend fails to render, mis-sends the CSRF header,
 * or drops the credentials flag — and each of those looks like a working app right up until someone
 * opens it.
 *
 * Both servers must already be running, and the API needs a raised throttle ceiling:
 *
 *   cd api && APP_THROTTLE_MAX_FAILURES=1000 mvn spring-boot:run
 *   cd web && npm run dev
 *
 * The raised ceiling is not a workaround for a bug. This suite calls from one address and deliberately
 * fails a login, so with the production default of 10 failures per 15 minutes it throttles itself
 * partway through — and every subsequent test then fails for a reason unrelated to what it tests. The
 * Spring integration tests raise the same setting for the same reason. `globalSetup` checks for it and
 * says so, rather than letting the suite fail confusingly.
 */
export default defineConfig({
  testDir: "./e2e",
  globalSetup: "./e2e/global-setup.ts",
  // A shared in-memory database means parallel tests would interleave account state.
  workers: 1,
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  reporter: [["list"]],
  use: {
    baseURL: process.env.WEB_BASE_URL ?? "http://localhost:3000",
    trace: "retain-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
