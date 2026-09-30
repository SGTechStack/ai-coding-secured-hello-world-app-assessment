import { expect, test, greeting, emailOf } from '../../fixtures/session';
import { emailedResetLink, resetPassword } from './reset-password';

const NEW_PASSWORD = 'N3w!Different#Pw-e2e';

test('AC2: a registered user resets from the emailed link, then logs in only with the new password', async ({ page, request, account }) => {
  const link = await emailedResetLink(request, emailOf(account.username));

  expect(await resetPassword(page, link, NEW_PASSWORD)).toBe(204);
  await expect(page).toHaveURL('/login?reset=done');
  await expect(page.getByRole('status')).toHaveText('Password updated. Log in with your new password.');
  // The token never lingers in the address bar or history.
  expect(await page.evaluate(() => window.location.hash)).toBe('');

  await page.getByLabel('Username').fill(account.username);
  await page.getByLabel('Password').fill(account.password);
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page.getByRole('alert')).toHaveText('Invalid username or password');

  await page.getByLabel('Password').fill(NEW_PASSWORD);
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(greeting(page, account.username)).toBeVisible();
});
