import { expect, test, logIn, csrfHeaders, greeting } from '../../fixtures/session';

const LOGOUT = '/api/auth/logout';

test('Logout after the Session already ended elsewhere recovers from the CSRF 403 and lands on the login screen', async ({ page, account }) => {
  await logIn(page, account);
  const statuses: number[] = [];
  page.on('response', (response) => { if (new URL(response.url()).pathname === LOGOUT) statuses.push(response.status()); });

  // The SPA fetches the Session's CSRF token only for its first mutating request, so make one Logout attempt fail
  // first: the tab then holds the live Session's token and stays signed in.
  await page.route(`**${LOGOUT}`, (route) => route.fulfill({ status: 503 }), { times: 1 });
  await page.getByRole('button', { name: 'Log out' }).click();
  await expect(page.getByRole('alert')).toHaveText("Couldn't log out. Check your connection and try again.");
  await expect(page.getByRole('button', { name: 'Log out' })).toBeEnabled();
  await expect(greeting(page, account.username)).toBeVisible();

  // End the Session behind the tab's back, through the same browser (as another tab would). The tab still shows the
  // protected application and still holds the dead Session's token.
  expect((await page.request.post(LOGOUT, { headers: await csrfHeaders(page.request) })).status()).toBe(204);

  await page.getByRole('button', { name: 'Log out' }).click();

  await expect(page).toHaveURL('/login');
  await expect(page.getByRole('heading', { name: 'Log in' })).toBeVisible();
  await expect(page.getByRole('alert')).toHaveCount(0);
  // CSRF is checked before anything else, so the dead Session first answers 403; the retry reaches the 401.
  expect(statuses).toEqual([503, 403, 401]);
});
