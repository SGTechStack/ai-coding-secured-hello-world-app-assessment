import { expect, test, logIn, greeting } from '../../fixtures/session';
import { callThroughApp } from './app-transport';

// A User refused the User list (403 ACCESS_DENIED), or calling it with a method it does not answer (405, ADR 0007),
// must keep their Session.
test('A User refused an admin call stays signed in', async ({ page, account }) => {
  await logIn(page, account);

  expect(await callThroughApp(page, 'GET', '/api/admin/users')).toEqual({ status: 403, code: 'ACCESS_DENIED' });
  expect(await callThroughApp(page, 'POST', '/api/admin/users')).toEqual({ status: 405, code: 'METHOD_NOT_ALLOWED' });

  await expect(page).toHaveURL('/home');
  await expect(greeting(page, account.username)).toBeVisible();
  // Still a live Session on both sides: Logout ends it for real.
  await page.getByRole('button', { name: 'Log out' }).click();
  await expect(page).toHaveURL('/login');
});
