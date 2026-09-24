import { defineConfig, devices } from "@playwright/test";

const CI = Boolean(process.env.CI);
// Override to run beside dev servers already on the defaults, e.g.
// `BACKEND_PORT=18080 FRONTEND_PORT=13000 npm run e2e`.
const BACKEND_PORT = process.env.BACKEND_PORT ?? "8080";
const FRONTEND_PORT = process.env.FRONTEND_PORT ?? "3000";
const BACKEND_URL = `http://localhost:${BACKEND_PORT}`;
const FRONTEND_URL = `http://localhost:${FRONTEND_PORT}`;

// End-to-end acceptance suite: the real SPA (Vite dev server) calling the real Spring Boot API
// (dev profile, seeded in-memory database) cross-origin, as in production. `npm run e2e` starts
// both servers.
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
      // The CORS allow-list must name the SPA's origin, whatever its port.
      env: {
        SERVER_PORT: BACKEND_PORT,
        APP_CORS_ALLOWED_ORIGINS: FRONTEND_URL,
      },
      url: `${BACKEND_URL}/api/v1/auth/csrf`,
      reuseExistingServer: !CI,
      timeout: 180_000,
    },
    {
      command: `npm run dev -- --port ${FRONTEND_PORT} --strictPort`,
      env: { VITE_API_BASE_URL: BACKEND_URL },
      url: `${FRONTEND_URL}/login`,
      reuseExistingServer: !CI,
    },
  ],
});
