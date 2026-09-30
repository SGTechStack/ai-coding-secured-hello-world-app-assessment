import { expect, test, enterCredentials, greeting } from '../fixtures/session';

// The shared fixture fails the test if the page logs any CSP console error.
test('login page loads without CSP console violations', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByRole('heading', { name: 'Log in' })).toBeVisible();
});

test('local seed user can log in and enter the protected application', async ({ page }) => {
  const csrfCalls: string[] = [];
  page.on('request', (request) => { if (new URL(request.url()).pathname === '/csrf') csrfCalls.push(request.url()); });
  await enterCredentials(page, 'johndoe', 'Password123!');
  // Rendering the login page contacts no server endpoint: the anonymous CSRF token is fetched on submit.
  expect(csrfCalls).toHaveLength(0);
  const csrfResponse = page.waitForResponse('**/csrf');
  // Hold the real login request until the pending state is asserted, so a fast backend cannot race past it.
  let release!: () => void;
  const held = new Promise<void>((resolve) => { release = resolve; });
  await page.route('**/api/auth/login', async (route) => { await held; await route.continue(); });
  const loginRequest = page.waitForRequest('**/api/auth/login');
  const loginResponse = page.waitForResponse('**/api/auth/login');
  await page.getByRole('button', { name: 'Log in' }).click();
  const csrf = await (await csrfResponse).json() as { token: string; headerName: string };
  const request = await loginRequest;
  expect(await request.headerValue(csrf.headerName)).toBe(csrf.token);
  expect(await request.headerValue('cookie')).toContain('id=');
  await expect(page.getByRole('button', { name: 'Logging in.' })).toBeDisabled();
  release();
  expect((await loginResponse).status()).toBe(200);
  await expect(page).toHaveURL('/home');
  await expect(greeting(page, 'johndoe')).toBeVisible();
  // Login drops the anonymous token; the authenticated session's token is fetched only on the next mutating request.
  expect(csrfCalls).toHaveLength(1);
});

test('malformed credentials show deferred corrective feedback without a login request', async ({ page }) => {
  const loginRequests: string[] = [];
  page.on('request', (request) => { if (new URL(request.url()).pathname === '/api/auth/login') loginRequests.push(request.url()); });

  await page.goto('/login');
  await page.getByLabel('Username').fill('bad/name');
  await expect(page.getByRole('alert')).toHaveCount(0);
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page.getByText('Username must not contain / or \\')).toBeVisible();
  await expect(page.getByText('Password is required')).toBeVisible();
  expect(loginRequests).toHaveLength(0);

  await page.getByLabel('Username').fill('johndoe');
  await expect(page.getByText('Username must not contain / or \\')).toHaveCount(0);
  await expect(page.getByText('Password is required')).toBeVisible();
});

test('invalid credentials retain form values and dismiss the safe banner when corrected', async ({ page }) => {
  await page.route('**/api/auth/login', (route) => route.fulfill({
    status: 401, contentType: 'application/problem+json', body: '{}',
  }));
  await enterCredentials(page, 'johndoe', 'Password123!');
  await page.getByRole('button', { name: 'Log in' }).click();

  await expect(page.getByRole('button', { name: 'Logging in.' })).toBeDisabled();
  await expect(page.getByRole('alert')).toHaveText('Invalid username or password');
  await expect(page.getByLabel('Username')).toHaveValue('johndoe');
  await expect(page.getByLabel('Password')).toHaveValue('Password123!');
  await expect(page.getByLabel('Username')).toBeEnabled();
  await page.getByLabel('Username').fill('johndoe2');
  await expect(page.getByRole('alert')).toHaveCount(0);
});


test('server and network failures show the same infrastructure feedback', async ({ page }) => {
  await page.route('**/api/auth/login', (route) => route.fulfill({
    status: 500, contentType: 'application/problem+json', body: '{}',
  }));
  await enterCredentials(page, 'johndoe', 'Password123!');
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page.getByRole('alert')).toHaveText('Unable to connect to the server. Please try again later.');

  await page.unroute('**/api/auth/login');
  await page.route('**/api/auth/login', (route) => route.abort('failed'));
  await page.getByLabel('Password').fill('Password123!x');
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page.getByRole('alert')).toHaveText('Unable to connect to the server. Please try again later.');
  await expect(page.getByLabel('Password')).toHaveValue('Password123!x');
});
