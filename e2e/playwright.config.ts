import { defineConfig, devices } from '@playwright/test';

// Runner config for the Secured Hello World E2E harness.
//
// Final evidence (video + trace) is forced HERE, at the campaign/runner level —
// never by a per-spec convention. Every run this config drives records a trace
// and a video, so Allure attachments are always inspectable.
//
// Campaign mode: when E2E_CAMPAIGN_SUITE is set, a campaign owns its own empty
// results and output directories via ALLURE_RESULTS_DIR and PLAYWRIGHT_OUTPUT_DIR.
// We never write campaign evidence into a shared default directory, and `--list`
// is rejected so a discovery pass cannot masquerade as an evidence run.

const campaignSuite = process.env.E2E_CAMPAIGN_SUITE?.trim();
const isCampaign = Boolean(campaignSuite);

if (isCampaign) {
  const allureResultsDir = process.env.ALLURE_RESULTS_DIR?.trim();
  const playwrightOutputDir = process.env.PLAYWRIGHT_OUTPUT_DIR?.trim();
  if (!allureResultsDir || !playwrightOutputDir) {
    throw new Error(
      'E2E_CAMPAIGN_SUITE requires campaign-owned ALLURE_RESULTS_DIR and PLAYWRIGHT_OUTPUT_DIR',
    );
  }
  if (process.argv.includes('--list')) {
    throw new Error('refusing to --list under E2E_CAMPAIGN_SUITE: an evidence run must execute');
  }
}

const baseURL = process.env.E2E_BASE_URL?.trim() || 'http://localhost:5173';
const allureResultsDir = process.env.ALLURE_RESULTS_DIR?.trim() || 'allure-results';
const outputDir = process.env.PLAYWRIGHT_OUTPUT_DIR?.trim() || 'test-results';

export default defineConfig({
  testDir: './tests',
  outputDir,
  // Foundation smoke and story evidence must be a real, ordered run: no retries
  // masking flakiness, single worker so one isolated lifecycle is exercised.
  fullyParallel: false,
  workers: 1,
  retries: 0,
  forbidOnly: true,
  reporter: [
    ['line'],
    [
      'allure-playwright',
      {
        resultsDir: allureResultsDir,
        // Attach the campaign suite so every result shares one Allure parentSuite.
        environmentInfo: campaignSuite ? { E2E_CAMPAIGN_SUITE: campaignSuite } : undefined,
      },
    ],
  ],
  use: {
    baseURL,
    // Forced final-evidence capture — config-level, applies to every test.
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
});
