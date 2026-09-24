import type { Page, Route } from "@playwright/test";
import { expect } from "./fixtures";

/** The demo account the backend seeds in its dev profile. */
export const JOHN = { username: "johndoe", password: "Password123!" };

export const LOGIN_API = "**/api/v1/auth/login";
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
