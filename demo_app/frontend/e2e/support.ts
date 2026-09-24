import type { Page, Route, TestInfo } from "@playwright/test";
import { expect } from "./fixtures";

/** The demo account the backend seeds in its dev profile. */
export const JOHN = { username: "johndoe", password: "Password123!" };

/** The bootstrap admin the backend creates in its dev profile (`app.admin.*`). */
export const ADMIN = { username: "admin", password: "Dev-Admin-Passw0rd!" };

export const LOGIN_API = "**/api/v1/auth/login";
export const ADMIN_USERS_PATH = "/api/v1/admin/users";
export const REGISTER_PATH = "/api/v1/auth/register";
export const LOGOUT_API = "**/api/v1/auth/logout";
export const ME_PATH = "/api/v1/auth/me";

/**
 * CORS headers for a faked API reply. The API is cross-origin, so without them the browser would
 * hide the reply from the SPA and the test would only exercise a network failure.
 */
export function corsHeaders(route: Route): Record<string, string> {
  return {
    "Access-Control-Allow-Origin": route.request().headers()["origin"] ?? "",
    "Access-Control-Allow-Credentials": "true",
  };
}

export const INVALID_CREDENTIALS = "Invalid username or password";
export const UNAVAILABLE =
  "Unable to connect to the server. Please try again later.";

/** The login page's controls, located the way a user (or screen reader) finds them. */
export function loginForm(page: Page) {
  return {
    username: page.getByLabel("Username"),
    password: page.getByLabel("Password"),
    // Its name changes to "Logging in..." while in flight.
    submit: page.getByRole("button", { name: /^Log(ging)? in/ }),
    banner: page.getByRole("alert"),
  };
}

/** The navbar's Log out button. Its name changes to "Logging out..." while in flight. */
export function logoutButton(page: Page) {
  return page.getByRole("button", { name: /^Log(ging)? out/ });
}

/** Signs in as John through the UI and waits for the landing page. */
export async function logInAsJohn(page: Page) {
  await page.goto("/login");
  const form = loginForm(page);
  await form.username.fill(JOHN.username);
  await form.password.fill(JOHN.password);
  await form.submit.click();
  await expect(
    page.getByRole("heading", { name: "Hello, John!" }),
  ).toBeVisible();
}

/** Signs in as the bootstrap admin through the UI and waits for the landing page. */
export async function logInAsAdmin(page: Page) {
  await page.goto("/login");
  const form = loginForm(page);
  await form.username.fill(ADMIN.username);
  await form.password.fill(ADMIN.password);
  await form.submit.click();
  await expect(
    page.getByRole("heading", { name: "Hello, Admin!" }),
  ).toBeVisible();
}

/** The navbar's Admin link, shown only to admins. */
export function adminLink(page: Page) {
  return page.getByRole("link", { name: "Admin", exact: true });
}

/** The admin user list's row for `username` (its row header). */
export function userRow(page: Page, username: string) {
  return page
    .getByRole("table", { name: "Users" })
    .getByRole("row")
    .filter({
      has: page.getByRole("rowheader", { name: username, exact: true }),
    });
}

/** The form is usable again: both inputs and the button are enabled. */
export async function expectFormEnabled(page: Page) {
  const form = loginForm(page);
  await expect(form.username).toBeEnabled();
  await expect(form.password).toBeEnabled();
  await expect(form.submit).toBeEnabled();
  await expect(form.submit).toHaveText("Log in");
}

/** The banner sits above the form, outside the `<form>` element. */
export async function expectBannerAboveForm(page: Page) {
  const form = loginForm(page);
  await expect(page.locator("form").getByRole("alert")).toHaveCount(0);
  const banner = await form.banner.boundingBox();
  const username = await form.username.boundingBox();
  expect(banner).not.toBeNull();
  expect(username).not.toBeNull();
  expect(banner!.y + banner!.height).toBeLessThanOrEqual(username!.y);
}

/** A registrable account. */
export type NewUser = {
  username: string;
  email: string;
  firstName: string;
  password: string;
};

/**
 * A fresh account no other test (or earlier run against a reused backend) has taken, so tests stay
 * independent under `fullyParallel`.
 */
export function uniqueUser(
  testInfo: TestInfo,
  overrides: Partial<NewUser> = {},
): NewUser {
  const id = `${Date.now().toString(36)}${testInfo.workerIndex}${Math.random().toString(36).slice(2, 6)}`;
  return {
    username: `e2e-${id}`,
    email: `e2e-${id}@example.com`,
    firstName: "Tess",
    password: `correct horse ${id}`,
    ...overrides,
  };
}

/** The registration page's controls, located the way a user (or screen reader) finds them. */
export function registrationForm(page: Page) {
  return {
    username: page.getByLabel("Username", { exact: true }),
    email: page.getByLabel("Email", { exact: true }),
    firstName: page.getByLabel("First name", { exact: true }),
    password: page.getByLabel("Password", { exact: true }),
    confirmPassword: page.getByLabel("Confirm password", { exact: true }),
    // Its name changes to "Creating account..." while in flight.
    submit: page.getByRole("button", { name: /^Creat(e|ing) account/ }),
  };
}

/**
 * Fills in and submits the registration form, once it is on screen (the login page also has a
 * "Username" field, which a fill could otherwise hit mid-navigation).
 */
export async function submitRegistration(page: Page, user: NewUser) {
  await expect(
    page.getByRole("heading", { name: "Create an account" }),
  ).toBeVisible();
  const form = registrationForm(page);
  await form.username.fill(user.username);
  await form.email.fill(user.email);
  await form.firstName.fill(user.firstName);
  await form.password.fill(user.password);
  await form.confirmPassword.fill(user.password);
  await form.submit.click();
}

/** Registers `user` through the UI and waits for the login page's notice. */
export async function registerThroughUi(page: Page, user: NewUser) {
  await page.goto("/register");
  await submitRegistration(page, user);
  await expect(page).toHaveURL("/login");
  await expect(page.getByRole("status")).toHaveText(REGISTERED_NOTICE);
}

export const REGISTERED_NOTICE = "Account created. Please log in.";

/** Logs in through the UI and waits for the greeting. */
export async function logInThroughUi(
  page: Page,
  { username, password, firstName }: NewUser,
) {
  await page.goto("/login");
  const form = loginForm(page);
  await form.username.fill(username);
  await form.password.fill(password);
  await form.submit.click();
  await expect(
    page.getByRole("heading", { name: `Hello, ${firstName}!` }),
  ).toBeVisible();
}
