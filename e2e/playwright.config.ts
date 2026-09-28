import { defineConfig, devices } from '@playwright/test';
import { defineBddConfig } from 'playwright-bdd';
import { env } from './support/env';

const testDir = defineBddConfig({
  features: 'features/**/*.feature',
  steps: ['steps/**/*.ts'],
  // Scenario titles double as traceability to the PRD (see README).
  outputDir: '.features-gen',
});

export default defineConfig({
  testDir,
  globalSetup: './support/global-setup.ts',
  fullyParallel: true,
  // Every scenario creates its own uniquely named accounts, so scenarios are
  // independent. Kept modest because BCrypt hashing is CPU-bound on the backend.
  workers: process.env.CI ? 2 : 4,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  use: {
    baseURL: env.frontendUrl,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  // Starts the apps if they are not already running (dev profile = in-memory H2,
  // H2 console enabled, non-Secure session cookie over plain HTTP).
  webServer: env.skipWebServer
    ? undefined
    : [
        {
          command: 'mvn -q -DskipTests spring-boot:run -Dspring-boot.run.profiles=dev',
          cwd: '../backend',
          url: `${env.backendUrl}/actuator/health`,
          reuseExistingServer: true,
          timeout: 180_000,
        },
        {
          command: 'npm run dev -- --strictPort',
          cwd: '../frontend',
          url: env.frontendUrl,
          reuseExistingServer: true,
          timeout: 60_000,
        },
      ],
});
