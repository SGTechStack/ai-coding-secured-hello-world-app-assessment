import { expect, test, logIn } from '../../fixtures/session';
import { sessionCookie } from './log-out';

test('AC1: logging out ends the Session, clears the session cookie and lands on the login screen', async ({ page, account }) => {
  await logIn(page, account);
  expect(await sessionCookie(page)).toBeDefined();

  const logout = page.waitForResponse((response) => new URL(response.url()).pathname === '/api/auth/logout');
  await page.getByRole('button', { name: 'Log out' }).click();

  expect((await logout).status()).toBe(204);
  await expect(page).toHaveURL('/login');
  await expect(page.getByRole('heading', { name: 'Log in' })).toBeVisible();
  expect(await sessionCookie(page)).toBeUndefined();

  // The protected application is gone: going back to it lands on the login screen again.
  await page.goto('/');
  await expect(page).toHaveURL('/login');
});

test.describe('on a phone-sized viewport', () => {
  test.use({ viewport: { width: 375, height: 740 }, hasTouch: true, isMobile: true });

  test('the Log out control is at least 44px tall and the header does not overflow', async ({ page, account }) => {
    await logIn(page, account);
    const button = page.getByRole('button', { name: 'Log out' });

    expect((await button.boundingBox())?.height ?? 0).toBeGreaterThanOrEqual(44);
    expect(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)).toBe(false);
  });
});
