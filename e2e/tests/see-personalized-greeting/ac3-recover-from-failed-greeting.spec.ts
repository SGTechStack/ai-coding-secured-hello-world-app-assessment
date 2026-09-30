import type { Page } from '@playwright/test';
import { expect, test, enterCredentials, greeting, type Account } from '../../fixtures/session';

const MESSAGE = 'Unable to load your greeting. Please try again.';

/** Logs in while the first GET /api/hello answers 500; later reads reach the real server. */
async function logInWithFailingGreeting(page: Page, account: Account) {
  await page.route('**/api/hello', (route) => route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
    { times: 1 });
  await enterCredentials(page, account.username, account.password);
  await page.getByRole('button', { name: 'Log in' }).click();
  const dialog = page.getByRole('alertdialog', { name: 'Unable to load your greeting' });
  await expect(dialog).toBeVisible();
  return dialog;
}

test('a failed Greeting opens a modal error dialog, and "Try again" shows the Greeting', async ({ page, account }) => {
  const dialog = await logInWithFailingGreeting(page, account);

  await expect(dialog).toHaveAccessibleDescription(MESSAGE);
  await expect(dialog.getByRole('button', { name: 'Try again' })).toBeFocused();
  // Opened with showModal(): the browser makes the page behind it inert and draws the backdrop.
  expect(await dialog.evaluate((element) => element.matches(':modal'))).toBe(true);

  await dialog.getByRole('button', { name: 'Try again' }).click();

  await expect(greeting(page, account.username)).toBeVisible();
  await expect(dialog).toBeHidden();
  await expect(page.getByRole('button', { name: 'Log out' })).toBeVisible();
});

for (const [how, dismiss] of [
  ['"Close"', (dialog) => dialog.getByRole('button', { name: 'Close' }).click()],
  ['Esc', (dialog) => dialog.page().keyboard.press('Escape')],
] as const satisfies readonly (readonly [string, (dialog: ReturnType<Page['getByRole']>) => Promise<void>])[]) {
  test(`${how} leaves "Welcome" with a focused inline "Try again" that recovers the Greeting`, async ({ page, account }) => {
    const dialog = await logInWithFailingGreeting(page, account);

    await dismiss(dialog);

    await expect(dialog).toBeHidden();
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Welcome');
    const inlineRetry = page.getByRole('button', { name: 'Try again' });
    await expect(inlineRetry).toBeFocused();

    await inlineRetry.click();
    await expect(greeting(page, account.username)).toBeVisible();
  });
}

test('at 375 px the dialog fits without horizontal scroll and its targets are at least 44 px', async ({ page, account }) => {
  await page.setViewportSize({ width: 375, height: 667 });
  const dialog = await logInWithFailingGreeting(page, account);

  const box = await dialog.boundingBox();
  expect(box!.x).toBeGreaterThanOrEqual(0);
  expect(box!.x + box!.width).toBeLessThanOrEqual(375);
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(375);
  for (const name of ['Try again', 'Close']) {
    const target = (await dialog.getByRole('button', { name }).boundingBox())!;
    expect(target.width, name).toBeGreaterThanOrEqual(44);
    expect(target.height, name).toBeGreaterThanOrEqual(44);
  }
});
