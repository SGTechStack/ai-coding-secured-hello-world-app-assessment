import { allure } from 'allure-playwright';
import type { Page } from '@playwright/test';
import { expect, test } from '../../fixtures/session';
import { acceptance, check } from '../../fixtures/acceptance';

const AC_DESCRIPTION = 'AC5: The CSRF token is session-backed and is not exposed through a JavaScript-readable cookie.';

const isLoginPost = (url: URL) => url.pathname === '/api/auth/login';
const isCsrf = (url: string) => new URL(url).pathname === '/csrf';
const problemCode = async (page: Page) => {
  const response = await page.waitForResponse((r) => isLoginPost(new URL(r.url())));
  const body = await response.json().catch(() => ({})) as { code?: string };
  return { status: response.status(), code: body.code ?? '' };
};

test(AC_DESCRIPTION, async ({ page, context, browser, baseURL }) => {
  await acceptance('US1: Establish the secure login application shell', AC_DESCRIPTION, 'Anonymous browser / page script');
  await allure.description(AC_DESCRIPTION);
  await allure.attachment('Test plan', [
    'Data:',
    '- Unregistered username e2e-session-probe with a wrong password. Discovery: a login attempt with the own-session token is evaluated (problem code INVALID_CREDENTIALS, shown as "Invalid username or password"); a token from another session is refused (problem code CSRF_TOKEN_REJECTED); document.cookie is empty and the jar holds only the HttpOnly session cookie "id".',
    'Isolation:',
    '- Isolated E2E stack: harness lifecycle (e2e/scripts/lifecycle.mjs) starts the backend with the local profile on a fresh in-memory H2 database; a second fresh browser context supplies the other session; the header swap is a browser-side route on this page only.',
    'Steps:',
    '1. Open /login, enter a username and wrong password and select Log in.',
    '2. Read document.cookie from the page script before and after the CSRF token was fetched.',
    '3. In a second, separate browser session, obtain that session\'s CSRF token from /csrf.',
    '4. Back in the first session, submit again while the login request carries the other session\'s token.',
    'Visible outcomes:',
    '- document.cookie never contains the CSRF token or any XSRF cookie',
    '- a CSRF token issued to one browser session is refused when replayed from another session',
    'Acceptance mapping:',
    '- AC5 | Story clause: not exposed through a JavaScript-readable cookie | Actor: Page script | Visible outcome: document.cookie never contains the CSRF token or any XSRF cookie',
    '- AC5 | Story clause: The CSRF token is session-backed | Actor: Anonymous browser | Visible outcome: a CSRF token issued to one browser session is refused when replayed from another session',
    'Assumptions:',
    '- The refusal code comes from the login response the browser itself received; the rendered alert shows the credentials were never evaluated.',
  ].join('\n'), 'text/plain');

  await page.goto('/login');
  const cookieBefore = await page.evaluate(() => document.cookie);
  await page.getByLabel('Username').fill('e2e-session-probe');
  await page.getByLabel('Password').fill('WrongPassword123!');
  const csrfResponse = page.waitForResponse((r) => isCsrf(r.url()));
  const ownAttempt = problemCode(page);
  await page.getByRole('button', { name: 'Log in', exact: true }).click();
  const own = await (await csrfResponse).json() as { token: string; headerName: string };
  const withOwn = await ownAttempt;
  const alert = page.getByRole('alert');
  await expect(alert).toBeVisible();
  const ownAlert = await alert.textContent();
  const cookieAfter = await page.evaluate(() => document.cookie);
  const jar = await context.cookies();
  await check('Assertion: [AC5] actor: Page script | document.cookie never contains the CSRF token or any XSRF cookie',
    'document.cookie holds neither the token nor an XSRF/CSRF cookie before or after /csrf; every jar cookie is HttpOnly',
    `document.cookie before "${cookieBefore}", after "${cookieAfter}"; jar ${jar.map((c) => `${c.name}(httpOnly=${c.httpOnly})`).join(',')}`, async () => {
      for (const value of [cookieBefore, cookieAfter]) {
        expect(value).not.toContain(own.token);
        expect(value).not.toMatch(/xsrf|csrf/i);
      }
      expect(jar.filter((c) => /xsrf|csrf/i.test(c.name))).toEqual([]);
      expect(jar.length).toBeGreaterThan(0);
      for (const cookie of jar) {
        expect(cookie.httpOnly).toBe(true);
        expect(cookie.value).not.toContain(own.token);
      }
    });

  const other = await browser.newContext({ baseURL, ignoreHTTPSErrors: true });
  const otherPage = await other.newPage();
  await otherPage.goto('/csrf');
  const foreign = JSON.parse(await otherPage.locator('body').innerText()) as { token: string };
  await other.close();

  await page.route(isLoginPost, (route) => route.continue({ headers: { ...route.request().headers(), [own.headerName.toLowerCase()]: foreign.token } }));
  await page.getByLabel('Password').fill('WrongPassword123!x');
  const foreignAttempt = problemCode(page);
  await page.getByRole('button', { name: 'Log in', exact: true }).click();
  const withForeign = await foreignAttempt;
  await expect(alert).toBeVisible();
  await expect(page.getByRole('button', { name: 'Log in', exact: true })).toBeEnabled();
  await alert.scrollIntoViewIfNeeded();
  const foreignAlert = await alert.textContent();
  await check('Assertion: [AC5] actor: Anonymous browser | a CSRF token issued to one browser session is refused when replayed from another session',
    'own-session token reaches the credential check ("Invalid username or password", INVALID_CREDENTIALS); the other session\'s token is refused before it (CSRF_TOKEN_REJECTED, credentials never evaluated)',
    `own: ${withOwn.status} ${withOwn.code}, alert "${ownAlert}"; other session: ${withForeign.status} ${withForeign.code}, alert "${foreignAlert}"`, async () => {
      expect(withOwn.code).toBe('INVALID_CREDENTIALS');
      expect(ownAlert).toBe('Invalid username or password');
      expect(withForeign.code).toBe('CSRF_TOKEN_REJECTED');
      await expect(alert).not.toHaveText('Invalid username or password');
    });
  await page.unroute(isLoginPost);
});
