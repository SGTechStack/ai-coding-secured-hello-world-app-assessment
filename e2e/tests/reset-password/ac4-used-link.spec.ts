import { expect, test, emailOf } from '../../fixtures/session';
import { emailedResetLink, resetPassword } from './reset-password';

test('AC4: a link that was already used once is rejected', async ({ page, request, account }) => {
  const link = await emailedResetLink(request, emailOf(account.username));
  expect(await resetPassword(page, link, 'N3w!Different#Pw-e2e')).toBe(204);
  await expect(page).toHaveURL('/login?reset=done');

  expect(await resetPassword(page, link, 'An0ther!Password#e2e')).toBe(400);
  await expect(page.getByRole('alert')).toHaveText('This reset link is invalid or has expired.');
});
