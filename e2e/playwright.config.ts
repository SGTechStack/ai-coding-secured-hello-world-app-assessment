import { defineConfig } from '@playwright/test';

const artifacts = '../artifacts/e2e';
// Specs that log in as a single shared account (fixtures/session.ts SEED_USER, or the bootstrapped ADMIN_USER). One
// Session per user means two of them in parallel expire each other's Session, so they run one at a time in their own
// project; everything else stays parallel.
const SEED_USER_SPECS = [
  '**/ac3-illustrative-account.spec.ts',
  '**/ac6-in-memory-token.spec.ts',
  '**/ac8-session-cookie-attributes.spec.ts',
  '**/login.smoke.spec.ts',
  '**/ac1-greeting-on-home-page.spec.ts',
  '**/admin-bootstrap.smoke.spec.ts',
  '**/admin-deletes-a-user.spec.ts',
];

export default defineConfig({
  testDir: './tests',
  forbidOnly: !!process.env.CI,
  outputDir: process.env.PLAYWRIGHT_OUTPUT_DIR ?? `${artifacts}/test-results`,
  reporter: [['list'], ['allure-playwright', { resultsDir: process.env.ALLURE_RESULTS_DIR ?? `${artifacts}/allure-results` }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'https://127.0.0.1:8443',
    // The local backend serves a generated self-signed certificate.
    ignoreHTTPSErrors: true,
    trace: 'on',
    video: 'on',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'seed-user', testMatch: SEED_USER_SPECS, workers: 1 },
    { name: 'scenario-accounts', testIgnore: SEED_USER_SPECS },
  ],
  // Its returned function is the global teardown.
  globalSetup: './scripts/global-setup.mjs',
});
