import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { defineConfig, devices } from '@playwright/test'
import { findLifecycle } from './fixtures/lifecycle'

const E2E_DIR = path.dirname(fileURLToPath(import.meta.url))
const campaign = process.env.E2E_CAMPAIGN_SUITE

if (campaign) {
  if (!process.env.ALLURE_RESULTS_DIR || !process.env.PLAYWRIGHT_OUTPUT_DIR) {
    throw new Error('E2E_CAMPAIGN_SUITE requires campaign-owned ALLURE_RESULTS_DIR and PLAYWRIGHT_OUTPUT_DIR')
  }
  if (process.argv.includes('--list')) {
    throw new Error('--list is not allowed for a campaign run: it produces no evidence')
  }
}

/** Final evidence always records video and trace; set by campaign runs, never by individual specs. */
const finalEvidence = Boolean(campaign) || process.env.E2E_EVIDENCE === 'final'
const lifecycle = findLifecycle()
const baseURL = process.env.E2E_BASE_URL ?? lifecycle?.webUrl

export default defineConfig({
  testDir: './tests',
  outputDir: process.env.PLAYWRIGHT_OUTPUT_DIR ?? path.join(E2E_DIR, '..', 'artifacts', 'e2e', 'test-results'),
  globalSetup: './scripts/global-setup.ts',
  // One shared stack per lifecycle (including the in-memory per-IP login throttle): run serially.
  workers: 1,
  fullyParallel: false,
  retries: 0,
  forbidOnly: true,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: [
    ['list'],
    [
      'allure-playwright',
      {
        resultsDir: process.env.ALLURE_RESULTS_DIR ?? path.join(E2E_DIR, '..', 'artifacts', 'e2e', 'allure-results'),
        detail: true,
        environmentInfo: {
          lifecycle_id: process.env.E2E_LIFECYCLE_ID ?? lifecycle?.id ?? 'unknown',
          lifecycle_mode: lifecycle?.mode ?? 'unknown',
          base_url: baseURL ?? 'unset',
          evidence: finalEvidence ? 'final (video + trace)' : 'retain-on-failure',
        },
      },
    ],
  ],
  use: {
    baseURL,
    video: finalEvidence ? 'on' : 'retain-on-failure',
    trace: finalEvidence ? 'on' : 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
})
