import { expect, test, emailOf } from '../../fixtures/session';

const REQUESTED = 'If an account is registered with that email, we have sent a link to reset its password. '
  + 'The link expires in 30 minutes.';

/** Requests a reset for `email` from the login page's link and returns the request's HTTP status. */
async function requestReset(page: import('@playwright/test').Page, email: string) {
  await page.goto('/login');
  await page.getByRole('link', { name: 'Forgot password?' }).click();
  await expect(page).toHaveURL('/forgot-password');
  await page.getByLabel('Email').fill(email);
  const response = page.waitForResponse((candidate) => new URL(candidate.url()).pathname === '/api/auth/password-reset');
  await page.getByRole('button', { name: 'Send reset link' }).click();
  const answered = await response;
  return { status: answered.status(), body: await answered.text() };
}

test('AC1: a registered and an unregistered email get the same answer and the same message', async ({ page, account }) => {
  const registered = await requestReset(page, emailOf(account.username));
  await expect(page.getByRole('status')).toHaveText(REQUESTED);

  const unregistered = await requestReset(page, `nobody${Date.now().toString(36)}@test.example.com`);
  await expect(page.getByRole('status')).toHaveText(REQUESTED);

  expect(registered.status).toBe(200);
  expect(unregistered).toEqual(registered);
});

test.describe('on a phone-sized viewport', () => {
  test.use({ viewport: { width: 375, height: 740 }, hasTouch: true, isMobile: true });

  test('the forgot-password controls are at least 44px tall and the page does not overflow', async ({ page }) => {
    await page.goto('/forgot-password');
    for (const control of [page.getByLabel('Email'), page.getByRole('button', { name: 'Send reset link' }),
      page.getByRole('link', { name: 'Log in' })]) {
      expect((await control.boundingBox())?.height ?? 0).toBeGreaterThanOrEqual(44);
    }
    expect(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)).toBe(false);
  });
});
