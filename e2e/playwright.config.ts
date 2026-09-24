import { defineConfig, devices } from '@playwright/test'

/**
 * Foundation runner config for the Hello World Auth E2E harness.
 *
 * Video and trace are forced ON here for EVERY run — never a per-spec
 * convention — so final evidence always carries inspectable attachments.
 *
 * Final campaign evidence flows through the E2E_CAMPAIGN_SUITE contract:
 *   - ALLURE_RESULTS_DIR   campaign-owned, empty results directory
 *   - PLAYWRIGHT_OUTPUT_DIR campaign-owned, empty output directory
 *   - E2E_CAMPAIGN_SUITE   the exact Allure parentSuite shared by the campaign
 * `--list` is rejected so a listing pass can never masquerade as evidence.
 *
 * The harness lifecycle (backend + Vite) is brought up out-of-band by
 * e2e/scripts/lifecycle.ps1; the base URL comes from E2E_BASE_URL (default
 * the dev Vite origin).
 */

const campaignSuite = process.env.E2E_CAMPAIGN_SUITE
const baseURL = process.env.E2E_BASE_URL ?? 'http://localhost:3000'

if (campaignSuite) {
  // A campaign run must not reuse or overwrite shared results/output.
  for (const key of ['ALLURE_RESULTS_DIR', 'PLAYWRIGHT_OUTPUT_DIR']) {
    if (!process.env[key]) {
      throw new Error(
        `E2E_CAMPAIGN_SUITE requires an explicit, campaign-owned ${key}.`,
      )
    }
  }
  if (process.argv.includes('--list')) {
    throw new Error('A campaign evidence run must not use --list.')
  }
}

const resultsDir = process.env.ALLURE_RESULTS_DIR ?? 'allure-results'
const outputDir = process.env.PLAYWRIGHT_OUTPUT_DIR ?? 'test-results'

export default defineConfig({
  testDir: './tests',
  outputDir,
  // One worker: the harness models isolation as a single backend lifecycle
  // with a shared in-memory DB; parallel workers would race that state.
  workers: 1,
  fullyParallel: false,
  forbidOnly: !!campaignSuite,
  reporter: [
    ['list'],
    [
      'allure-playwright',
      {
        resultsDir,
        environmentInfo: {
          base_url: baseURL,
          campaign_suite: campaignSuite ?? '(none)',
        },
      },
    ],
  ],
  use: {
    baseURL,
    // Forced evidence capture — not a per-spec convention.
    video: 'on',
    trace: 'on',
    screenshot: 'on',
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
})
