import { expect, test } from "@playwright/test";

/**
 * Screenshots the themed screens. Not an assertion suite — a way to look at the result without
 * clicking through it by hand.
 *
 * Opt-in, because it depends on particular accounts existing and would otherwise make the real suite
 * order-dependent. Skipped rather than excluded by config, so the default run still lists it and says
 * why it did not run:
 *
 *   $env:CAPTURE=1; npx playwright test capture-theme     # PowerShell
 *   CAPTURE=1 npx playwright test capture-theme           # bash
 */
test("capture the themed screens", async ({ page }) => {
  test.skip(
    !process.env.CAPTURE,
    "Screenshot utility. Set CAPTURE=1 to run, with an admin account and a locked account present.",
  );

  await page.setViewportSize({ width: 1280, height: 820 });

  await page.goto("/login");
  await expect(page.getByRole("heading", { name: "Log in" })).toBeVisible();
  await page.screenshot({ path: "screenshots/01-login.png" });

  // A failed login, so the error styling is captured rather than described.
  await page.getByLabel("Username").fill("lockdemo");
  await page.getByLabel("Password").fill("wrong-on-purpose");
  await page.getByRole("button", { name: "Log in" }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await page.screenshot({ path: "screenshots/02-login-error.png" });

  await page.goto("/register");
  await expect(page.getByRole("heading", { name: "Create an account" })).toBeVisible();
  await page.screenshot({ path: "screenshots/03-register.png" });

  await page.goto("/login");
  await page.getByLabel("Username").fill("admin");
  await page.getByLabel("Password").fill("dev-admin-password");
  await page.getByRole("button", { name: "Log in" }).click();
  await expect(page.getByText("Hello, admin")).toBeVisible();
  await page.screenshot({ path: "screenshots/04-home.png" });

  await page.getByRole("link", { name: "Accounts" }).click();
  await expect(page.getByRole("table")).toBeVisible();
  // The locked account from the lockout demo shows up here with its badge.
  await expect(page.getByText("Locked out")).toBeVisible();
  await page.screenshot({ path: "screenshots/05-admin-accounts.png" });

  // And the confirmation dialog on a destructive action.
  const row = page.getByRole("row", { name: /lockdemo/ });
  await row.getByRole("button", { name: "Delete" }).click();
  await expect(page.getByRole("heading", { name: /Delete lockdemo/ })).toBeVisible();
  await page.screenshot({ path: "screenshots/06-confirm-dialog.png" });
});
