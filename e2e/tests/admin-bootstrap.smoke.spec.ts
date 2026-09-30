import { expect, test, logIn, ADMIN_USER } from '../fixtures/session';

/**
 * Admin bootstrap smoke (Story 12, ADR 0009): the Admin the harness seeded through ADMIN_USERNAME / ADMIN_PASSWORD
 * signs in through the ordinary login form and lands on the protected Home page, whose Greeting names the Admin's
 * canonical username. This proves the seeded password was hashed like any other account's.
 */
test('the bootstrapped Admin signs in and reaches the protected Home page', async ({ page }) => {
  await logIn(page, ADMIN_USER);

  await expect(page).toHaveURL('/home');
  await expect(page.getByRole('heading', { name: `Hello, ${ADMIN_USER.username}` })).toBeVisible();
});
