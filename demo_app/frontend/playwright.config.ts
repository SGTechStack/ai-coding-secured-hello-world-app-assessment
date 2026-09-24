import { defineConfig, devices } from "@playwright/test";

const CI = Boolean(process.env.CI);
// Override to run beside dev servers already on the defaults, e.g.
// `BACKEND_PORT=18080 FRONTEND_PORT=15173 npm run e2e`.
const BACKEND_PORT = process.env.BACKEND_PORT ?? "8080";
const FRONTEND_PORT = process.env.FRONTEND_PORT ?? "5173";
const FRONTEND_URL = `http://localhost:${FRONTEND_PORT}`;

// End-to-end acceptance suite: the real SPA (Vite dev server, which proxies /api) against the real
// Spring Boot API (dev profile) with its seeded in-memory database. `npm run e2e` starts both servers.
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
      env: { SERVER_PORT: BACKEND_PORT },
      url: `http://localhost:${BACKEND_PORT}/api/v1/auth/csrf`,
      reuseExistingServer: !CI,
      timeout: 180_000,
    },
    {
      command: `npm run dev -- --port ${FRONTEND_PORT} --strictPort`,
      // vite.config.ts reads BACKEND_PORT for its /api proxy target.
      env: { BACKEND_PORT },
      url: `${FRONTEND_URL}/login`,
      reuseExistingServer: !CI,
    },
  ],
});
