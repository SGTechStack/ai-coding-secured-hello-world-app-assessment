import type { Page } from '@playwright/test';
import { emailOf, expect, test, greeting } from '../fixtures/session';

const PASSWORD = 'Str0ng!Passw0rd-e2e';
const REGISTER_PATH = '/api/auth/register';

/** Synthetic, run-unique identity so repeated runs against the same lifecycle never collide. */
function identity(prefix: string) {
  const suffix = `${Date.now().toString(36)}${Math.floor(Math.random() * 1_000)}`;
  const username = `${prefix}${suffix}`;
  return { username, email: emailOf(username) };
}

async function fillForm(page: Page, values: { username: string; email: string; password?: string; confirm?: string }) {
  await page.getByLabel('Username').fill(values.username);
  await page.getByLabel('Email').fill(values.email);
  await page.getByLabel('Password', { exact: true }).fill(values.password ?? PASSWORD);
  await page.getByLabel('Confirm password').fill(values.confirm ?? values.password ?? PASSWORD);
}

test('registration page and its lazy strength meter load without CSP violations', async ({ page }) => {
  // The shared fixture fails the test if the page logs any CSP console error.
  await page.goto('/login');
  await page.getByRole('link', { name: 'Create an account' }).click();
  await expect(page).toHaveURL('/register');
  await expect(page.getByRole('heading', { name: 'Create account' })).toBeVisible();
  await page.getByLabel('Password', { exact: true }).fill('correct horse battery staple');
  await expect(page.getByText(/^Strength: (Very weak|Weak|Fair|Good|Strong)$/)).toBeVisible();
});

test('a visitor registers with a CSRF-protected request, is sent to login, and can then log in', async ({ page }) => {
  const account = identity('e2euser');
  const csrfCalls: string[] = [];
  page.on('request', (candidate) => { if (new URL(candidate.url()).pathname === '/csrf') csrfCalls.push(candidate.url()); });
  await page.goto('/register');
  await fillForm(page, { username: account.username.toUpperCase(), email: account.email });
  // Rendering the public form creates no session: the CSRF token is fetched only when the visitor submits.
  expect(csrfCalls).toHaveLength(0);
  const csrfResponse = page.waitForResponse('**/csrf');
  const request = page.waitForRequest(`**${REGISTER_PATH}`);
  const response = page.waitForResponse(`**${REGISTER_PATH}`);
  await page.getByRole('button', { name: 'Create account' }).click();

  const csrf = await (await csrfResponse).json() as { token: string; headerName: string };
  const sent = await request;
  expect(await sent.headerValue(csrf.headerName)).toBe(csrf.token);
  expect(JSON.parse(sent.postData() ?? '{}')).toEqual({
    username: account.username.toUpperCase(), email: account.email, password: PASSWORD,
  });
  expect((await response).status()).toBe(201);
  await expect(page.getByRole('status')).toHaveText('Account created. You can now log in.');
  await expect(page).toHaveURL('/login');

  const stored = await page.evaluate(() => JSON.stringify({ ...localStorage }) + JSON.stringify({ ...sessionStorage }));
  expect(stored).not.toContain(PASSWORD);
  expect(stored).not.toContain(account.email);

  // Usernames are normalised to lowercase, so the account logs in with the lowercase form.
  await page.getByLabel('Username').fill(account.username);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page).toHaveURL('/home');
  await expect(greeting(page, account.username)).toBeVisible();
});

test('a duplicate username or email gets the combined message and keeps the drafts', async ({ page }) => {
  const account = identity('e2edup');
  await page.goto('/register');
  await fillForm(page, account);
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page).toHaveURL('/login');

  await page.goto('/register');
  await fillForm(page, { username: identity('e2eother').username, email: account.email.toUpperCase() });
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByRole('alert')).toHaveText('Username or email is already in use.');
  await expect(page.getByLabel('Password', { exact: true })).toHaveValue(PASSWORD);
});

test.describe('on a phone-sized viewport', () => {
  test.use({ viewport: { width: 375, height: 740 }, hasTouch: true, isMobile: true });

  test('controls stay labelled and at least 44px tall', async ({ page }) => {
    await page.goto('/register');
    for (const control of [
      page.getByLabel('Username'), page.getByLabel('Email'), page.getByLabel('Password', { exact: true }),
      page.getByLabel('Confirm password'), page.getByRole('button', { name: 'Show password', exact: true }),
      page.getByRole('button', { name: 'Create account' }), page.getByRole('link', { name: 'Log in' }),
    ]) {
      await expect(control).toBeVisible();
      expect((await control.boundingBox())?.height ?? 0).toBeGreaterThanOrEqual(44);
    }
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
    expect(overflow).toBe(false);
  });
});

// The lock lasts the rest of the lifecycle, but only for this test's own source address (see fixtures/session.ts).
test('ten rejected submissions lock the source with a generic 429 and no Retry-After', async ({ page }) => {
  await page.goto('/register');
  // Same-origin browser requests carry the SameSite=Strict session cookie and its CSRF token, like the SPA does.
  const statuses = await page.evaluate(async (path) => {
    const csrf = await (await fetch('/csrf')).json() as { token: string; headerName: string };
    const results: number[] = [];
    // Earlier specs in this lifecycle may already have counted rejections from this source, so stop at the lock.
    for (let attempt = 0; attempt < 10; attempt += 1) {
      const response = await fetch(path, {
        method: 'POST', headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
        body: JSON.stringify({ username: 'ab', email: 'bad', password: 'x' }),
      });
      results.push(response.status);
      if (response.status !== 400) break;
    }
    return results;
  }, REGISTER_PATH);
  expect(statuses.filter((status) => status !== 400).every((status) => status === 429)).toBe(true);

  await fillForm(page, identity('e2elocked'));
  const response = page.waitForResponse(`**${REGISTER_PATH}`);
  await page.getByRole('button', { name: 'Create account' }).click();
  const locked = await response;
  expect(locked.status()).toBe(429);
  expect(locked.headers()['retry-after']).toBeUndefined();
  await expect(page.getByRole('alert')).toHaveText('Registration is temporarily unavailable. Please try again later.');
});
