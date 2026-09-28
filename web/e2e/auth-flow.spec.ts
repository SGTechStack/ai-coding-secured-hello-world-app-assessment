import { expect, test, type Page } from "@playwright/test";

/**
 * The frontend, in a real browser.
 *
 * Every assertion here is about something only a browser can tell us: that the app renders, that the
 * session cookie survives a cross-origin request, that the CSRF header is attached, that the router
 * sends people where it should. The security rules themselves are tested server-side, where they are
 * enforced.
 */

const PASSWORD = "correct-horse-battery";

/** Unique per run. The dev database is in-memory and lives as long as the API process. */
function uniqueUsername(prefix: string) {
  return `${prefix}${Date.now().toString(36)}${Math.floor(Math.random() * 1000)}`;
}

async function register(page: Page, username: string) {
  await page.goto("/register");
  await page.getByLabel("Username").fill(username);
  await page.getByLabel("Email").fill(`${username}@example.com`);
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Create account" }).click();
  // Waits for the redirect before returning. Without this, a caller that logs straight in can race
  // the registration request and fail on a 401 for an account that is still being created — which
  // reads like a broken login rather than a broken helper.
  await expect(page.getByText("Account created. Log in to continue.")).toBeVisible();
}

async function logIn(page: Page, username: string, password = PASSWORD) {
  await page.goto("/login");
  await page.getByLabel("Username").fill(username);
  await page.getByLabel("Password").fill(password);
  await page.getByRole("button", { name: "Log in" }).click();
}

test("a visitor asking for the home page is sent to log in", async ({ page }) => {
  await page.goto("/");

  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole("heading", { name: "Log in" })).toBeVisible();
});

test("registering leads to the login page, not to a session", async ({ page }) => {
  const username = uniqueUsername("alice");

  await register(page, username);

  // Decision 6: registration creates an account and nothing else. Landing anywhere but the login
  // page would mean a session was issued here.
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByText("Account created. Log in to continue.")).toBeVisible();
});

test("a registered user logs in and sees the greeting from the protected endpoint", async ({
  page,
}) => {
  const username = uniqueUsername("bob");
  await register(page, username);

  await logIn(page, username);

  await expect(page).toHaveURL(new RegExp("/$"));
  // The text came back from GET /api/hello, so this passing means the session cookie made it across
  // origins and the server accepted it.
  await expect(page.getByText(`Hello, ${username}`)).toBeVisible();
});

test("a wrong password shows the server's generic message", async ({ page }) => {
  const username = uniqueUsername("carol");
  await register(page, username);

  await logIn(page, username, "definitely-not-the-password");

  await expect(page.getByRole("alert")).toContainText("Invalid username or password");
  await expect(page).toHaveURL(/\/login$/);
});

test("logging out ends the session and the home page is closed again", async ({ page }) => {
  const username = uniqueUsername("dave");
  await register(page, username);
  await logIn(page, username);
  await expect(page.getByText(`Hello, ${username}`)).toBeVisible();

  await page.getByRole("button", { name: "Log out" }).click();
  await expect(page).toHaveURL(/\/login$/);

  // Not just "the UI forgot" — asking for the protected page again has to bounce.
  await page.goto("/");
  await expect(page).toHaveURL(/\/login$/);
});

test("a USER sees no admin navigation and cannot open the admin page", async ({ page }) => {
  const username = uniqueUsername("erin");
  await register(page, username);
  await logIn(page, username);
  // Confirms the session exists before anything is concluded from its absence. Without this, a
  // failed login would make the next two assertions pass for entirely the wrong reason.
  await expect(page.getByText(`Hello, ${username}`)).toBeVisible();

  await expect(page.getByRole("link", { name: "Accounts" })).toHaveCount(0);

  await page.goto("/admin");
  await expect(page).toHaveURL(new RegExp("/$"));
  await expect(page.getByText(`Hello, ${username}`)).toBeVisible();
});

test("an admin lists accounts and disables one", async ({ page }) => {
  const victim = uniqueUsername("frank");
  await register(page, victim);

  await logIn(page, "admin", "dev-admin-password");
  await page.getByRole("link", { name: "Accounts" }).click();

  const row = page.getByRole("row", { name: new RegExp(victim) });
  await expect(row).toBeVisible();
  await expect(row).toContainText("Enabled");

  await row.getByRole("button", { name: "Disable" }).click();
  // The confirmation step, which the destructive and privilege-changing actions all go through.
  await page.getByRole("button", { name: "Disable", exact: true }).last().click();

  await expect(page.getByRole("status")).toContainText(`${victim} is disabled`);
  await expect(page.getByRole("row", { name: new RegExp(victim) })).toContainText("Disabled");
});

test("an admin cannot act on their own account", async ({ page }) => {
  await logIn(page, "admin", "dev-admin-password");
  await page.getByRole("link", { name: "Accounts" }).click();

  const ownRow = page.getByRole("row", { name: /admin.*\(you\)/ });
  await expect(ownRow).toBeVisible();
  // Disabled in the UI as a courtesy; the server answers 409 regardless, which the API tests cover.
  await expect(ownRow.getByRole("button", { name: "Delete" })).toBeDisabled();
});

test("the account listing never renders a password hash", async ({ page }) => {
  await logIn(page, "admin", "dev-admin-password");
  await page.getByRole("link", { name: "Accounts" }).click();
  await expect(page.getByRole("table")).toBeVisible();

  const rendered = await page.content();
  expect(rendered).not.toContain("$2a$");
  expect(rendered).not.toContain("$2b$");
  expect(rendered).not.toContain("passwordHash");
});
