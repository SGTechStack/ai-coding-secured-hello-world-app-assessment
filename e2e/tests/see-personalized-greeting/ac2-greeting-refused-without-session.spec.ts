import { expect, test, greeting, logIn, returnToTab, csrfHeaders } from '../../fixtures/session';
import { sessionCookie } from '../log-out/log-out';

test('AC2: GET /api/hello is Authentication-required without a Session and with a cookie replayed after Logout', async ({ page, account, playwright }) => {
  // A separate client with no cookie jar of its own.
  const outsider = await playwright.request.newContext({ baseURL: test.info().project.use.baseURL, ignoreHTTPSErrors: true });
  const hello = (cookie?: string) => outsider.get('/api/hello', { headers: cookie ? { Cookie: `id=${cookie}` } : {} });
  const expectAuthenticationRequired = async (response: Awaited<ReturnType<typeof hello>>) => {
    expect(response.status()).toBe(401);
    expect((await response.json() as { code: string }).code).toBe('AUTHENTICATION_REQUIRED');
  };
  try {
    await expectAuthenticationRequired(await hello());

    await logIn(page, account);
    const captured = (await sessionCookie(page))?.value;
    expect((await hello(captured)).status(), 'the cookie is live before Logout').toBe(200);

    await page.getByRole('button', { name: 'Log out' }).click();
    await expect(page).toHaveURL('/login');

    await expectAuthenticationRequired(await hello(captured));
  } finally {
    await outsider.dispose();
  }
});

test('AC2: a Home page whose Session was ended server-side lands on the login screen', async ({ page, account }) => {
  await logIn(page, account);

  // End the Session behind the tab's back, through the same browser (as another tab would).
  expect((await page.request.post('/api/auth/logout', { headers: await csrfHeaders(page.request) })).status()).toBe(204);

  const hello = page.waitForResponse((response) => new URL(response.url()).pathname === '/api/hello');
  await returnToTab(page);
  expect((await hello).status()).toBe(401);

  await expect(page).toHaveURL('/login');
  await expect(page.getByRole('heading', { name: 'Log in' })).toBeVisible();
  // Nothing private survives: going back re-runs the guard against the cleared client state.
  await page.goBack();
  await expect(page).toHaveURL('/login');
  await expect(greeting(page, account.username)).toHaveCount(0);
});
