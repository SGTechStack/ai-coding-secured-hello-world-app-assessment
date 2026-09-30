import { csrfHeaders, emailOf, expect, test } from '../../fixtures/session';
import { emailedResetLink, resetPassword, TOKEN_LIFETIME_MS } from './reset-password';

test('AC3: an expired link is rejected and the password is unchanged', async ({ page, request, account }) => {
  test.setTimeout(TOKEN_LIFETIME_MS + 60_000);
  const link = await emailedResetLink(request, emailOf(account.username));
  await page.waitForTimeout(TOKEN_LIFETIME_MS + 1_000);

  expect(await resetPassword(page, link, 'N3w!Different#Pw-e2e')).toBe(400);
  await expect(page.getByRole('alert')).toHaveText('This reset link is invalid or has expired.');
  await expect(page.getByRole('link', { name: 'Request a new reset link' })).toBeVisible();

  const login = await request.post('/api/auth/login', {
    headers: await csrfHeaders(request), data: { username: account.username, password: account.password },
  });
  expect(login.status()).toBe(200);
});
