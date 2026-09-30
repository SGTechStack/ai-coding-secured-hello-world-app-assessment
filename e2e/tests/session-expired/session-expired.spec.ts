import { expect, test, greeting, logIn, returnToTab, csrfHeaders } from '../../fixtures/session';
import { callThroughApp } from './app-transport';

const LOGOUT = '/api/auth/logout';

test('A Greeting read after the Session was replaced by a login elsewhere lands on the login screen', async ({ page, account, request }) => {
  await logIn(page, account);

  // The same User logs in from another client (its own cookie jar): one Session per User, so the tab's is expired.
  expect((await request.post('/api/auth/login', { headers: await csrfHeaders(request), data: account })).status())
    .toBe(200);

  const hello = page.waitForResponse((response) => new URL(response.url()).pathname === '/api/hello');
  await returnToTab(page);
  expect((await hello).status()).toBe(401);
  expect((await (await hello).json() as { code: string }).code).toBe('AUTHENTICATION_REQUIRED');

  await expect(page).toHaveURL('/login');
  await expect(page.getByRole('heading', { name: 'Log in' })).toBeVisible();
  await expect(page.getByRole('alert')).toHaveCount(0);
  await page.goBack();
  await expect(page).toHaveURL('/login');
});

test('A mutating call after the Session ended server-side lands on the login screen', async ({ page, account }) => {
  await logIn(page, account);
  // A first mutating call makes the tab fetch and hold the live Session's CSRF token. It is refused (no POST handler),
  // which must not end the Session.
  expect(await callThroughApp(page, 'POST', '/api/hello')).toEqual({ status: 405, code: 'METHOD_NOT_ALLOWED' });
  await expect(greeting(page, account.username)).toBeVisible();

  // End the Session behind the tab's back, through the same browser (as another tab would).
  expect((await page.request.post(LOGOUT, { headers: await csrfHeaders(page.request) })).status()).toBe(204);

  // The tab still sends the dead Session's token: CSRF is checked first, so this is the Session expired signal.
  expect(await callThroughApp(page, 'POST', '/api/hello')).toEqual({ status: 403, code: 'CSRF_TOKEN_REJECTED' });

  await expect(page).toHaveURL('/login');
  await expect(page.getByRole('heading', { name: 'Log in' })).toBeVisible();
  await expect(page.getByRole('alert')).toHaveCount(0);
});
