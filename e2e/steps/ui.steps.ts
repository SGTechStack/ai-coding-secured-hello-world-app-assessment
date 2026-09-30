import { expect, type Page } from '@playwright/test';
import { env } from '../support/env';
import { newTestUser, type World } from '../support/world';
import { Given, Then, When } from './fixtures';

// Locators follow the app's accessible names (see frontend/src/*.tsx).
const loginForm = (page: Page) => page.getByRole('form', { name: 'Log in' });
const registerForm = (page: Page) => page.getByRole('form', { name: 'Register' });
const changePasswordForm = (page: Page) => page.getByRole('form', { name: 'Change password' });
const greeting = (page: Page) => page.getByRole('heading', { name: /^Hello, / });
const userRow = (page: Page, username: string) =>
  page.getByRole('row').filter({ has: page.getByRole('cell', { name: username, exact: true }) });

async function openApp(page: Page, path = '/') {
  await page.goto(path);
  // The SPA resolves its auth state via /api/hello before rendering a view.
  await expect(page.getByText('Loading…')).toHaveCount(0);
}

async function submitLogin(page: Page, username: string, password: string) {
  await openApp(page);
  const form = loginForm(page);
  await form.getByLabel('Username').fill(username);
  await form.getByLabel('Password').fill(password);
  await form.getByRole('button', { name: 'Log in' }).click();
}

async function submitRegistration(page: Page, world: World, payload: { username: string; email: string; password: string }) {
  world.attemptedRegistration = payload;
  await openApp(page);
  await loginForm(page).getByRole('button', { name: 'Register' }).click();
  const form = registerForm(page);
  await form.getByLabel('Username').fill(payload.username);
  await form.getByLabel('Email').fill(payload.email);
  await form.getByLabel('Password').fill(payload.password);
  await form.getByRole('button', { name: 'Register' }).click();
}

// ---------------------------------------------------------------------------
// Navigation and forms
// ---------------------------------------------------------------------------

When('an anonymous visitor opens the app', async ({ page }) => {
  await openApp(page);
});

When('the visitor registers {string} through the registration form', async ({ page, world }, alias: string) => {
  const { username, email, password } = world.user(alias);
  await submitRegistration(page, world, { username, email, password });
});

When(
  'the visitor registers {string} through the registration form reusing the username of {string}',
  async ({ page, world }, alias: string, existing: string) => {
    const { email, password } = world.user(alias);
    await submitRegistration(page, world, { username: world.user(existing).username, email, password });
  },
);

Given('{string} is logged in through the login form', async ({ page, world }, alias: string) => {
  const user = world.user(alias);
  await submitLogin(page, user.username, user.password);
  await expect(greeting(page)).toHaveText(`Hello, ${user.username}`);
});

When('{string} logs in through the login form', async ({ page, world }, alias: string) => {
  const user = world.user(alias);
  await submitLogin(page, user.username, user.password);
});

When('someone submits the login form for {string} with a wrong password', async ({ page, world }, alias: string) => {
  await submitLogin(page, world.user(alias).username, 'Definitely-Wrong-Password-1');
});

When('someone submits the login form for a username that is unknown with a wrong password', async ({ page }) => {
  await submitLogin(page, newTestUser('ghost').username, 'Definitely-Wrong-Password-1');
});

When('the user clicks {string}', async ({ page }, name: string) => {
  await page.getByRole('button', { name, exact: true }).click();
});

When('the page is reloaded', async ({ page }) => {
  await page.reload();
  await expect(page.getByText('Loading…')).toHaveCount(0);
});

async function requestResetThroughForm(page: Page, email: string) {
  await openApp(page);
  await page.getByRole('button', { name: 'Forgot password?' }).click();
  const form = page.getByRole('form', { name: 'Request password reset' });
  await form.getByLabel('Email').fill(email);
  await form.getByRole('button', { name: 'Send reset link' }).click();
}

When(
  'an anonymous visitor requests a password reset through the form for the email of {string}',
  async ({ page, world }, alias: string) => {
    await requestResetThroughForm(page, world.user(alias).email);
  },
);

When('an anonymous visitor requests a password reset through the form for an unregistered email', async ({ page }) => {
  await requestResetThroughForm(page, `nobody.${Date.now()}@example.test`);
});

When(
  '{string} opens the reset link and sets the new password {string}',
  async ({ page, world }, alias: string, newPassword: string) => {
    const token = world.resetTokens.get(alias);
    if (!token) throw new Error(`No reset token known for ${alias}`);
    // Same link shape PasswordResetService mails: <frontend>/reset-password?token=...
    await openApp(page, `/reset-password?token=${encodeURIComponent(token)}`);
    const form = page.getByRole('form', { name: 'Set new password' });
    await expect(form.getByLabel('Reset token')).toHaveValue(token);
    await form.getByLabel('New password').fill(newPassword);
    await form.getByRole('button', { name: 'Reset password' }).click();
    await expect(page.getByRole('heading', { name: 'Password reset' })).toBeVisible();
    world.user(alias).password = newPassword;
  },
);

When('the admin clicks {string} in the row of {string}', async ({ page, world }, button: string, alias: string) => {
  await userRow(page, world.user(alias).username).getByRole('button', { name: button, exact: true }).click();
});

// ---------------------------------------------------------------------------
// Assertions on the page
// ---------------------------------------------------------------------------

Then('the login form is shown', async ({ page }) => {
  await expect(loginForm(page)).toBeVisible();
});

Then('the change-password form is shown', async ({ page }) => {
  await expect(changePasswordForm(page)).toBeVisible();
});

When(
  '{string} changes their password from their original password to {string} through the form',
  async ({ page, world }, alias: string, newPassword: string) => {
    const user = world.user(alias);
    const form = changePasswordForm(page);
    await form.getByLabel('Current password').fill(user.originalPassword);
    await form.getByLabel('New password').fill(newPassword);
    await form.getByRole('button', { name: 'Change password' }).click();
    user.password = newPassword;
  },
);

Then('the registration form is still shown', async ({ page }) => {
  const form = registerForm(page);
  await expect(form).toBeVisible();
  // Native constraint validation (minLength=12) blocks submission client-side.
  const valid = await form.getByLabel('Password').evaluate((el) => (el as HTMLInputElement).checkValidity());
  expect(valid).toBe(false);
});

Then('the page greets {string}', async ({ page, world }, alias: string) => {
  await expect(greeting(page)).toHaveText(`Hello, ${world.user(alias).username}`);
});

Then('the page does not greet anyone', async ({ page }) => {
  await expect(greeting(page)).toHaveCount(0);
});

Then('the page shows the alert {string}', async ({ page }, text: string) => {
  await expect(page.getByRole('alert')).toHaveText(text);
});

Then('the page shows the status {string}', async ({ page }, text: string) => {
  await expect(page.getByRole('status')).toHaveText(text);
});

Then('there is no {string} button', async ({ page }, name: string) => {
  await expect(page.getByRole('button', { name, exact: true })).toHaveCount(0);
});

Then('the browser holds an HttpOnly {string} session cookie', async ({ page }, name: string) => {
  const cookie = (await page.context().cookies(env.backendUrl)).find((c) => c.name === name);
  expect(cookie, `${name} cookie in browser`).toBeDefined();
  expect(cookie!.httpOnly).toBe(true);
  expect(await page.evaluate(() => document.cookie)).not.toContain(`${name}=`);
});

Then('the browser session cannot access {string}', async ({ page }, path: string) => {
  // page.request shares the browser context's cookie jar.
  expect((await page.request.get(`${env.backendUrl}${path}`)).status()).toBe(401);
});

Then('the users table has the columns {string}', async ({ page }, columns: string) => {
  const expected = columns.split(',').map((c) => c.trim());
  await expect(page.getByRole('columnheader')).toHaveText(expected);
});

Then(
  'the users table shows {string} with role {string} and enabled {string}',
  async ({ page, world }, alias: string, role: string, enabled: string) => {
    const user = world.user(alias);
    const cells = userRow(page, user.username).getByRole('cell');
    await expect(cells.nth(0)).toHaveText(user.username);
    await expect(cells.nth(1)).toHaveText(user.email);
    await expect(cells.nth(2)).toHaveText(role);
    await expect(cells.nth(3)).toHaveText(enabled);
    await expect(cells.nth(4)).not.toBeEmpty();
  },
);

Then('the users table marks the row of {string} as {string}', async ({ page, world }, alias: string, text: string) => {
  const row = userRow(page, world.user(alias).username);
  await expect(row.getByRole('cell').last()).toHaveText(text);
  await expect(row.getByRole('button')).toHaveCount(0);
});

Then('the users table does not show {string}', async ({ page, world }, alias: string) => {
  await expect(userRow(page, world.user(alias).username)).toHaveCount(0);
});
