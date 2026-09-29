import { defineConfig, devices } from '@playwright/test'

// Level E runs the production build under `vite preview` against the real backend (ticket 10). Fixed ports keep the
// two origins on each other's allow-lists: the SPA's API origin is baked in at build time (R-BLD-012), and the
// backend's CORS allow-list names the SPA origin. Override with PW_PREVIEW_PORT / PW_API_PORT if these are taken.
const previewPort = Number(process.env.PW_PREVIEW_PORT ?? 15110)
const apiPort = Number(process.env.PW_API_PORT ?? 18010)
const spaOrigin = `http://localhost:${previewPort}`
const apiOrigin = `http://localhost:${apiPort}`

// Read by `vite build` in the web server below and by the specs' own `loadEnv`, so both see the same policy.
process.env.VITE_API_ORIGIN = apiOrigin

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: { baseURL: spaOrigin, trace: 'retain-on-failure' },
  // Level E runs on Chromium and Firefox only (R-FE-006).
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'firefox', use: { ...devices['Desktop Firefox'] } },
  ],
  webServer: [
    {
      // The real application from the backend's test sources (sg.securedhello.e2e.E2eBackend): dev profile, a fresh
      // H2 file, test-only secrets and the fixture accounts. Nothing test-only is in the production build.
      command:
        'mvn -q -f ../backend/pom.xml test-compile spring-boot:test-run ' +
        '-Dspring-boot.run.main-class=sg.securedhello.e2e.E2eBackend ' +
        `-Dspring-boot.run.arguments="--server.port=${apiPort} --app.origins.spa=${spaOrigin} --app.origins.api=${apiOrigin} --app.audit.directory=target/e2e-logs"`,
      url: `${apiOrigin}/actuator/health`,
      reuseExistingServer: false,
      timeout: 300_000,
    },
    {
      // `vite preview` of the production build is the verification surface for the document policy (ADR-060).
      command: `npm run build && npx vite preview --port ${previewPort} --strictPort`,
      url: spaOrigin,
      reuseExistingServer: false,
      timeout: 180_000,
    },
  ],
})
