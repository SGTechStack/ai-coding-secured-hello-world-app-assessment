import { defineConfig, devices } from "@playwright/test";
import path from "node:path";

const CI = Boolean(process.env.CI);
// Override to run beside dev servers already on the defaults, e.g.
// `BACKEND_PORT=18080 FRONTEND_PORT=13000 npm run e2e`.
const BACKEND_PORT = process.env.BACKEND_PORT ?? "8080";
const FRONTEND_PORT = process.env.FRONTEND_PORT ?? "3000";
const BACKEND_URL = `http://localhost:${BACKEND_PORT}`;
const FRONTEND_URL = `http://localhost:${FRONTEND_PORT}`;
// The backend also logs to this file, which is where the password reset specs read the emailed
// link (the stub email service logs it; Playwright can't read a webServer's stdout). One file per
// port, so suites on other ports don't mix. Workers load this config too, so they see the variable.
process.env.E2E_BACKEND_LOG ??= path.resolve(
  `../backend/target/e2e-backend-${BACKEND_PORT}.log`,
);

// End-to-end acceptance suite: the SPA's production build, served by `vite preview` with the
// production CSP and security headers, calling the real Spring Boot API (dev profile, seeded
// in-memory database) cross-origin, as in production. `npm run e2e` starts both servers. Every
// spec uses the fixture in e2e/fixtures.ts, which fails a test on any CSP violation.
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: CI,
  reporter: CI ? "github" : "list",
  use: {
    baseURL: FRONTEND_URL,
    trace: "retain-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: [
    {
      // The dev profile seeds johndoe and keeps the session cookie usable over plain HTTP.
      command: "mvn -q spring-boot:run -Dspring-boot.run.profiles=dev",
      cwd: "../backend",
      // The CORS allow-list must name the SPA's origin, whatever its port. Every test shares one
      // client IP, so the registration, reset-request and failed-login throttles (10, 10 and 20
      // per IP per 15 minutes by default) are raised; the API tests cover the real limits.
      env: {
        SERVER_PORT: BACKEND_PORT,
        APP_CORS_ALLOWED_ORIGINS: FRONTEND_URL,
        APP_SECURITY_THROTTLE_REGISTRATION_MAX_ATTEMPTS: "1000",
        APP_SECURITY_THROTTLE_LOGIN_MAX_ATTEMPTS: "1000",
        APP_SECURITY_THROTTLE_PASSWORD_RESET_REQUEST_MAX_ATTEMPTS: "1000",
        // Reset links must point at this SPA, whatever its port.
        APP_FRONTEND_URL: FRONTEND_URL,
        LOGGING_FILE_NAME: process.env.E2E_BACKEND_LOG,
      },
      url: `${BACKEND_URL}/api/v1/auth/csrf`,
      reuseExistingServer: !CI,
      timeout: 180_000,
    },
    {
      // VITE_API_BASE_URL is baked in at build time, and the CSP's connect-src is built from it.
      command: `npm run build && npm run preview -- --port ${FRONTEND_PORT} --strictPort`,
      env: { VITE_API_BASE_URL: BACKEND_URL },
      timeout: 120_000,
      url: `${FRONTEND_URL}/login`,
      reuseExistingServer: !CI,
    },
  ],
});
