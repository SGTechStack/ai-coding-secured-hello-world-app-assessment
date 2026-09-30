import { expect, test, enterCredentials, greeting, logIn, SEED_USER } from '../../fixtures/session';

const isHello = (url: string) => new URL(url).pathname === '/api/hello';

test('AC1: the seeded User logs in and the Home page shows "Hello, johndoe" from GET /api/hello', async ({ page }) => {
  const hello = page.waitForResponse((response) => isHello(response.url()));
  await logIn(page, SEED_USER);

  await expect(page).toHaveURL('/home');
  expect((await hello).status()).toBe(200);
  expect(await (await hello).json()).toEqual({ message: 'Hello, johndoe' });
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Hello, johndoe');
  await expect(page.getByRole('heading', { name: 'Protected application' })).toHaveCount(0);
});

test('AC1: a login typed with other case and surrounding whitespace is greeted by the canonical username', async ({ page }) => {
  await enterCredentials(page, '  JohnDoe ', SEED_USER.password);
  const hello = page.waitForResponse((response) => isHello(response.url()));
  await page.getByRole('button', { name: 'Log in' }).click();

  expect((await hello).status()).toBe(200);
  await expect(greeting(page, 'johndoe')).toBeVisible();
});
