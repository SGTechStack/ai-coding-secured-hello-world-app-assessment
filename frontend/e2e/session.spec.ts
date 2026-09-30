import { expect, test } from '@playwright/test';
import { API_ORIGIN } from './origins';

test('a Regular user registers, logs in, sees the greeting and logs out, and the old session cookie is then rejected', async ({
  page,
  context,
  playwright,
}) => {
  const username = 'testuser1';
  const password = 'correct-horse-battery-1';

  await page.goto('/register');
  await page.getByLabel('Username', { exact: true }).fill(username);
  await page.getByLabel('Email', { exact: true }).fill('testuser1@test.example.com');
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Register' }).click();

  await expect(page.getByText('Account created. Please log in.')).toBeVisible();
  await page.getByLabel('Username', { exact: true }).fill(username);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Log in' }).click();

  await expect(page.getByRole('heading', { name: `Hello, ${username}` })).toBeVisible();
  const session = (await context.cookies(API_ORIGIN)).find((cookie) => cookie.name === 'SESSION');
  expect(session).toBeDefined();

  await page.getByRole('button', { name: 'Log out' }).click();

  await expect(page.getByRole('heading', { name: 'Log in' })).toBeVisible();
  const attacker = await playwright.request.newContext();
  const replayed = await attacker.get(`${API_ORIGIN}/api/hello`, { headers: { Cookie: `SESSION=${session!.value}` } });
  expect(replayed.status()).toBe(401);
  await attacker.dispose();
});
