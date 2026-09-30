import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { test, expect } from '@playwright/test';

// Foundation smoke test.
//
// Purpose: prove the foundation can drive a real browser against the running
// stack (navigation + rendered backend health) and that the documented,
// harness-owned reset command works. It does NOT test a product story.
//
// This spec is a foundation-owned file (protected by e2e-foundation.json). It
// invokes the DOCUMENTED harness lifecycle command (`lifecycle.mjs reset`) — it
// never calls a private backend endpoint to reset state. Final evidence (video +
// trace) is forced by playwright.config.ts, not by anything in this file.

const execFileAsync = promisify(execFile);
const HERE = dirname(fileURLToPath(import.meta.url));
const LIFECYCLE = resolve(HERE, '..', 'scripts', 'lifecycle.mjs');

async function harnessReset(): Promise<void> {
  // Reset only through the documented harness command (fresh in-memory H2 +
  // re-seeded admin via a scoped backend restart), never a hidden API.
  await execFileAsync(process.execPath, [LIFECYCLE, 'reset'], {
    cwd: resolve(HERE, '..'),
    timeout: 240_000,
  });
}

test.describe('foundation smoke', () => {
  test('navigation + rendered backend health, then harness reset', async ({ page }) => {
    // This scenario drives a real harness reset (backend process restart + health
    // regate), which routinely exceeds Playwright's default 30s test timeout.
    test.setTimeout(180_000);

    // 1. Navigation: the browser reaches the frontend at the configured base URL.
    await page.goto('/');
    await expect(page.getByRole('heading', { name: 'Secured Hello World' })).toBeVisible();

    // 2. Health: the app renders backend connectivity (the live-region text turns
    //    from "Checking…" into a "Backend reachable — status UP" message). This
    //    proves the browser -> frontend -> backend /api/health path end to end.
    await expect(page.getByText(/Backend reachable — status/i)).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/Backend reachable — status\s+UP/i)).toBeVisible();

    // 3. Reset contract: invoke the documented harness reset and re-verify the
    //    app still renders healthy connectivity against the fresh backend.
    await harnessReset();
    await page.goto('/');
    await expect(page.getByText(/Backend reachable — status\s+UP/i)).toBeVisible({ timeout: 30_000 });
  });
});
