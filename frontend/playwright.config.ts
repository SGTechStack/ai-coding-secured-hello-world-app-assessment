import { defineConfig, devices } from '@playwright/test';
import { API_ORIGIN, PWNED_PASSWORDS_ORIGIN, SPA_ORIGIN } from './e2e/origins';

const mvnw = process.platform === 'win32' ? '.\\mvnw.cmd' : './mvnw';

/**
 * The one end-to-end test runs the real SPA and API on their separate origins, so it is the only
 * test that exercises real CORS, cookies and the same-site setup. The API runs in the dev profile
 * and is always started fresh, so its in-memory database is empty and test identities are fixed.
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  use: {
    baseURL: SPA_ORIGIN,
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      command: `node e2e/pwned-passwords-stub.mjs ${new URL(PWNED_PASSWORDS_ORIGIN).port}`,
      url: `${PWNED_PASSWORDS_ORIGIN}/range/00000`,
      reuseExistingServer: false,
    },
    {
      command:
        `${mvnw} -q spring-boot:run -Dspring-boot.run.profiles=dev ` +
        `-Dspring-boot.run.arguments=--app.password.compromised-check.base-url=${PWNED_PASSWORDS_ORIGIN}/range/`,
      cwd: '../backend',
      url: `${API_ORIGIN}/actuator/health`,
      reuseExistingServer: false,
      timeout: 180_000,
    },
    {
      command: 'npm run dev',
      url: SPA_ORIGIN,
      // The SPA holds no data, so a dev server that is already running will do.
      reuseExistingServer: !process.env.CI,
    },
  ],
});
