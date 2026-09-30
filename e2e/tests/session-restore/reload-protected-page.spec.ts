import type { Page } from '@playwright/test';
import { expect, test, greeting, logIn } from '../../fixtures/session';

const path = (url: string) => new URL(url).pathname;

/** Records the API calls the page makes, in order, as "METHOD /path". */
function recordApiCalls(page: Page) {
  const calls: string[] = [];
  page.on('request', (request) => {
    const pathname = path(request.url());
    if (pathname.startsWith('/api/') || pathname === '/csrf') calls.push(`${request.method()} ${pathname}`);
  });
  return calls;
}

test('reloading the Home page restores the live Session: profile first, then its CSRF token, then the Greeting', async ({ page, account }) => {
  await logIn(page, account);
  const calls = recordApiCalls(page);
  const profile = page.waitForResponse((response) => path(response.url()) === '/api/profile');

  await page.reload();

  await expect(greeting(page, account.username)).toBeVisible();
  await expect(page).toHaveURL('/home');
  const body = await (await profile).json() as { id: string; role: string };
  expect(Object.keys(body).sort()).toEqual(['id', 'role']);
  expect(body.role).toBe('USER');
  expect(calls.slice(0, 3)).toEqual(['GET /api/profile', 'GET /csrf', 'GET /api/hello']);

  // The restored tab holds the live Session's CSRF token, so Logout works first time.
  const logout = page.waitForResponse((response) => path(response.url()) === '/api/auth/logout');
  await page.getByRole('button', { name: 'Log out' }).click();
  expect((await logout).status()).toBe(204);
  await expect(page).toHaveURL('/login');
});

test('a placeholder shaped like the page frame shows while the Session is checked', async ({ page, account }) => {
  await logIn(page, account);
  let release!: () => void;
  const held = new Promise<void>((resolve) => { release = resolve; });
  await page.route('**/api/profile', async (route) => { await held; await route.continue(); }, { times: 1 });

  await page.reload();

  const placeholder = page.getByRole('status', { name: 'Restoring your session' });
  await expect(placeholder).toBeVisible();
  await expect(placeholder).toHaveAttribute('aria-busy', 'true');
  release();
  await expect(greeting(page, account.username)).toBeVisible();
  await expect(placeholder).toHaveCount(0);
});

test('a profile answered Authentication-required sends the reloaded page to /login and fetches no CSRF token', async ({ page, account }) => {
  await logIn(page, account);
  await page.route('**/api/profile', (route) => route.fulfill({
    status: 401, contentType: 'application/problem+json', body: JSON.stringify({ code: 'AUTHENTICATION_REQUIRED' }),
  }), { times: 1 });
  const calls = recordApiCalls(page);

  await page.reload();

  await expect(page).toHaveURL('/login');
  await expect(page.getByRole('heading', { name: 'Log in' })).toBeVisible();
  expect(calls).toEqual(['GET /api/profile']);
});

test('a network error while checking the Session shows a dialog, and "Try again" restores it', async ({ page, account }) => {
  await logIn(page, account);
  await page.route('**/api/profile', (route) => route.abort('failed'), { times: 1 });

  await page.reload();

  const dialog = page.getByRole('alertdialog', { name: 'Unable to reach the server' });
  await expect(dialog).toBeVisible();
  await expect(dialog).toHaveAccessibleDescription('Check your connection, then try again.');
  await expect(dialog.getByRole('button', { name: 'Try again' })).toBeFocused();
  // Nothing behind the dialog to return to, so Esc keeps it open.
  await page.keyboard.press('Escape');
  await expect(dialog).toBeVisible();

  await dialog.getByRole('button', { name: 'Try again' }).click();

  await expect(greeting(page, account.username)).toBeVisible();
  await expect(dialog).toHaveCount(0);
});

test('"Go to login" leaves the failed check for the login screen', async ({ page, account }) => {
  await logIn(page, account);
  await page.route('**/api/profile', (route) => route.abort('failed'), { times: 1 });
  await page.reload();

  await page.getByRole('alertdialog').getByRole('button', { name: 'Go to login' }).click();

  await expect(page).toHaveURL('/login');
});

test('reloading /login makes no Session check, even with a live session cookie', async ({ page, account }) => {
  await logIn(page, account);
  await page.goto('/login');
  const calls = recordApiCalls(page);

  await page.reload();

  await expect(page.getByRole('heading', { name: 'Log in' })).toBeVisible();
  expect(calls).toEqual([]);
});
