import { test as base, expect, type APIRequestContext, type Page } from '@playwright/test';

export type Account = { username: string; password: string };

const PASSWORD = 'Str0ng!Passw0rd-e2e';

/** Seed account from the local profile changelog only (backend/local/); permitted E2E oracle per e2e/README.md. */
export const SEED_USER: Account = { username: 'johndoe', password: 'Password123!' };

/**
 * The Admin created by Admin bootstrap on the harness backend (e2e/scripts/lifecycle.mjs), overridable with
 * E2E_ADMIN_USERNAME / E2E_ADMIN_PASSWORD. It exists only in harness-started runs, never in a deployed environment.
 * The username is already canonical (lowercase), as stored. The defaults must match lifecycle.mjs's ADMIN_* defaults.
 */
export const ADMIN_USER: Account = {
  username: process.env.E2E_ADMIN_USERNAME ?? 'e2eadmin',
  password: process.env.E2E_ADMIN_PASSWORD ?? 'Str0ng!Passw0rd-e2e',
};

/** The email every scenario-owned account registers with. */
export const emailOf = (username: string) => `${username}@test.example.com`;

/**
 * The value of cookie `name` in a raw Cookie or Set-Cookie header. For a request's Cookie header or a response's
 * Set-Cookie: the browser jar only shows the latest id, not what one exchange sent or issued.
 */
export const cookieValue = (header: string | null | undefined, name: string) =>
  (header ?? '').split(/[;\n]/).map((part) => part.trim()).find((part) => part.startsWith(`${name}=`))?.slice(name.length + 1);

/** Fetches the CSRF token of `request`'s session and returns it as the server-advertised header. */
export async function csrfHeaders(request: APIRequestContext): Promise<Record<string, string>> {
  const csrf = await (await request.get('/csrf')).json() as { token: string; headerName: string };
  return { [csrf.headerName]: csrf.token };
}

/**
 * Shared foundation test object: every test fails if the page logs a Content-Security-Policy console error.
 * `account` registers a scenario-owned user through the public registration API, so parallel scenarios never share
 * a login (one session per user would expire each other's sessions).
 */
export const test = base.extend<{ cspViolations: string[]; account: Account }>({
  // Per-IP limits (login rate limit, registration lock) are keyed on the client address. The backend trusts
  // X-Forwarded-For from 127.0.0.1 as a proxy, so each test gets its own address (198.18.0.0/15, a benchmarking range)
  // and parallel scenarios never spend each other's allowance.
  extraHTTPHeaders: async ({ extraHTTPHeaders }, use) => {
    const octet = () => Math.floor(Math.random() * 254) + 1;
    await use({ ...extraHTTPHeaders, 'X-Forwarded-For': `198.18.${octet()}.${octet()}` });
  },
  cspViolations: [async ({ page }, use) => {
    const violations: string[] = [];
    page.on('console', (message) => {
      if (message.type() === 'error' && message.text().includes('Content-Security-Policy')) violations.push(message.text());
    });
    await use(violations);
    expect(violations).toEqual([]);
  }, { auto: true }],
  account: async ({ request }, use) => {
    const username = `e2e${Date.now().toString(36)}${Math.floor(Math.random() * 100_000)}`;
    const created = await request.post('/api/auth/register', {
      headers: await csrfHeaders(request),
      data: { username, email: emailOf(username), password: PASSWORD },
    });
    expect(created.status(), `register ${username}`).toBe(201);
    await use({ username, password: PASSWORD });
  },
});
export { expect };

/** Opens the login screen and types the credentials, without submitting. */
export async function enterCredentials(page: Page, username: string, password: string) {
  await page.goto('/login');
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Password').fill(password);
}

/** The Home page's heading once the server has greeted `username`. */
export const greeting = (page: Page, username: string) => page.getByRole('heading', { name: `Hello, ${username}` });

/**
 * The User switches back to this tab. The SPA's query layer refetches stale reads on focus, so the Home page reads
 * its Greeting again, as a real tab switch would make it do.
 */
export async function returnToTab(page: Page) {
  await page.evaluate(() => window.dispatchEvent(new Event('visibilitychange')));
}

/** Logs `account` in through the real login screen and waits for the Home page's Greeting. */
export async function logIn(page: Page, account: Account) {
  await enterCredentials(page, account.username, account.password);
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(greeting(page, account.username)).toBeVisible();
}
