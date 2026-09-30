import { randomBytes } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import type { APIRequestContext, Page } from '@playwright/test';
import { csrfHeaders } from '../../fixtures/session';

/** The E2E backend runs with the local profile, whose stub EmailService logs each reset link here (ADR 0004 §5). */
const BACKEND_LOG = resolve(import.meta.dirname, '../../../backend/logs/spring.log');
/** Must match the lifecycle's PASSWORD_RESET_TOKEN_LIFETIME (scripts/lifecycle.mjs). */
export const TOKEN_LIFETIME_MS = 45_000;

type LinkLogLine = { event?: { action?: string }; url?: { full?: string } };

/**
 * Requests a reset for `email` through the API with a W3C traceparent, then reads the emailed link from the backend
 * log line carrying that trace id, so parallel scenarios never pick up each other's links. Returns the link's path and
 * fragment, to open against the E2E base URL.
 */
export async function emailedResetLink(request: APIRequestContext, email: string): Promise<string> {
  const traceId = randomBytes(16).toString('hex');
  const response = await request.post('/api/auth/password-reset', {
    headers: { ...await csrfHeaders(request), traceparent: `00-${traceId}-${randomBytes(8).toString('hex')}-01` },
    data: { email },
  });
  if (response.status() !== 200) throw new Error(`reset request answered ${response.status()}`);

  // The link is issued in the background after the response, so poll briefly.
  const deadline = Date.now() + 10_000;
  while (Date.now() < deadline) {
    const lines = (await readFile(BACKEND_LOG, 'utf8')).split('\n').filter((line) => line.includes(traceId));
    for (const line of lines) {
      let entry: LinkLogLine;
      try { entry = JSON.parse(line) as LinkLogLine; } catch { continue; } // A line still being written.
      if (entry.event?.action === 'reset_link_stubbed' && entry.url?.full) {
        const link = new URL(entry.url.full);
        return `${link.pathname}${link.hash}`;
      }
    }
    await new Promise((delay) => setTimeout(delay, 250));
  }
  throw new Error(`no reset link was logged for trace ${traceId}`);
}

/** Opens the reset link and submits `password` as the new password. */
export async function resetPassword(page: Page, link: string, password: string) {
  await page.goto(link);
  await page.getByLabel('New password', { exact: true }).fill(password);
  await page.getByLabel('Confirm new password').fill(password);
  const response = page.waitForResponse((candidate) =>
    new URL(candidate.url()).pathname === '/api/auth/password-reset/confirm');
  await page.getByRole('button', { name: 'Reset password' }).click();
  return (await response).status();
}
