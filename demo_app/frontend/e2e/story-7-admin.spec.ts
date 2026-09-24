import type { Browser, Page } from "@playwright/test";
import { expect, test } from "./fixtures";
import {
  ADMIN,
  ADMIN_USERS_PATH,
  INVALID_CREDENTIALS,
  JOHN,
  adminLink,
  loginForm,
  logoutButton,
  logInThroughUi,
  logInAsAdmin,
  logInAsJohn,
  registerThroughUi,
  uniqueUser,
  userRow,
  type NewUser,
} from "./support";

const OWN_ACCOUNT_NOTE = "You can't change your own account.";

/**
 * Registers `user`, then logs in as the admin and opens the user list, all in the test's own
 * page (the CSP fixture watches it).
 */
async function adminWithFreshUser(page: Page, user: NewUser) {
  await registerThroughUi(page, user);
  await logInAsAdmin(page);
  await adminLink(page).click();
  await expect(userRow(page, user.username)).toBeVisible();
}

/**
 * A second browser context for the target user, so the admin's session in the test's page stays
 * put. The caller closes it. It is outside the CSP fixture's context, like Story 6's.
 */
async function otherBrowser(browser: Browser) {
  const context = await browser.newContext();
  const page = await context.newPage();
  return { page, close: () => context.close() };
}

/** Tries to log `user` in on `page` and expects the generic invalid-credentials banner. */
async function expectLoginRefused(page: Page, user: NewUser) {
  await page.goto("/login");
  const form = loginForm(page);
  await form.username.fill(user.username);
  await form.password.fill(user.password);
  await form.submit.click();
  await expect(form.banner).toHaveText(INVALID_CREDENTIALS);
}

test.describe("Story 7: Admin", () => {
  test("Scenario 1: The bootstrap admin sees the Admin link and the user list including johndoe, with no password data", async ({
    page,
  }) => {
    await logInAsAdmin(page);

    const listed = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname === ADMIN_USERS_PATH &&
        response.request().method() === "GET",
    );
    await adminLink(page).click();
    await expect(page).toHaveURL("/admin/users");

    const table = page.getByRole("table", { name: "Users" });
    await expect(table).toBeVisible();
    await expect(table.getByRole("columnheader")).toHaveText([
      "Username",
      "Email",
      "First name",
      "Role",
      "Status",
      "Created",
      "Actions",
    ]);
    const john = userRow(page, JOHN.username);
    await expect(john).toContainText("johndoe@example.com");
    await expect(john).toContainText("USER");
    await expect(john).toContainText("Enabled");
    await expect(userRow(page, ADMIN.username)).toContainText("ADMIN");

    await test.step("no password data on screen or on the wire", async () => {
      // The headers above show there is no password column; no hash leaks into any cell either.
      await expect(table).not.toContainText(/\$2[aby]\$/);
      const rows = (await (await listed).json()) as Record<string, unknown>[];
      for (const row of rows) {
        expect(Object.keys(row).sort()).toEqual([
          "createdAt",
          "email",
          "enabled",
          "firstName",
          "id",
          "role",
          "username",
        ]);
      }
    });
  });

  test("Scenario 2: The admin disables a fresh user, who can no longer log in; re-enabled, they can", async ({
    page,
    browser,
  }, testInfo) => {
    const user = uniqueUser(testInfo);
    await adminWithFreshUser(page, user);
    const target = await otherBrowser(browser);
    try {
      await logInThroughUi(target.page, user);

      const row = userRow(page, user.username);
      await row.getByRole("button", { name: "Disable" }).click();
      await expect(row).toContainText("Disabled");
      await expect(row.getByRole("button", { name: "Enable" })).toBeVisible();

      await test.step("their live session has ended", async () => {
        await target.page.reload();
        await expect(target.page).toHaveURL("/login");
      });
      await test.step("they can't log in", async () => {
        await expectLoginRefused(target.page, user);
      });

      await row.getByRole("button", { name: "Enable" }).click();
      await expect(row).toContainText("Enabled");
      await test.step("re-enabled, they can log in", async () => {
        await logInThroughUi(target.page, user);
      });
    } finally {
      await target.close();
    }
  });

  test("Scenario 3: The admin promotes a user, who sees the Admin link after logging in again", async ({
    page,
    browser,
  }, testInfo) => {
    const user = uniqueUser(testInfo);
    await adminWithFreshUser(page, user);
    const target = await otherBrowser(browser);
    try {
      await logInThroughUi(target.page, user);
      await expect(adminLink(target.page)).toHaveCount(0);

      const row = userRow(page, user.username);
      await row.getByRole("button", { name: "Make admin" }).click();
      await expect(row).toContainText("ADMIN");
      await expect(
        row.getByRole("button", { name: "Make user" }),
      ).toBeVisible();

      await logInThroughUi(target.page, user);
      await expect(adminLink(target.page)).toBeVisible();
      await adminLink(target.page).click();
      await expect(target.page).toHaveURL("/admin/users");
      await expect(
        target.page.getByRole("table", { name: "Users" }),
      ).toBeVisible();
    } finally {
      await target.close();
    }
  });

  test("Scenario 4: The admin deletes a user after confirming, and the row disappears", async ({
    page,
  }, testInfo) => {
    const user = uniqueUser(testInfo);
    await adminWithFreshUser(page, user);
    const row = userRow(page, user.username);

    await test.step("cancelling keeps the user", async () => {
      await row.getByRole("button", { name: "Delete" }).click();
      const dialog = page.getByRole("alertdialog", {
        name: `Delete ${user.username}?`,
      });
      await expect(dialog).toBeVisible();
      await expect(
        dialog.getByRole("button", { name: "Cancel" }),
      ).toBeFocused();
      await dialog.getByRole("button", { name: "Cancel" }).click();
      await expect(dialog).toBeHidden();
      await expect(row).toBeVisible();
    });

    await row.getByRole("button", { name: "Delete" }).click();
    const dialog = page.getByRole("alertdialog", {
      name: `Delete ${user.username}?`,
    });
    await dialog.getByRole("button", { name: "Delete" }).click();
    await expect(dialog).toBeHidden();
    await expect(row).toHaveCount(0);

    await test.step("the deleted user can't log in", async () => {
      await logoutButton(page).click();
      await expectLoginRefused(page, user);
    });
  });

  test("Scenario 5: The admin's own row has its controls disabled", async ({
    page,
  }) => {
    await logInAsAdmin(page);
    await adminLink(page).click();

    const own = userRow(page, ADMIN.username);
    const controls = own.getByRole("button");
    await expect(controls).toHaveText(["Disable", "Make user", "Delete"]);
    for (const control of await controls.all()) {
      await expect(control).toBeDisabled();
      await expect(control).toHaveAccessibleDescription(OWN_ACCOUNT_NOTE);
    }
    await expect(own).toContainText(OWN_ACCOUNT_NOTE);
  });

  test("Scenario 6: johndoe has no Admin link, and /admin/users lands on /", async ({
    page,
  }) => {
    await logInAsJohn(page);
    await expect(adminLink(page)).toHaveCount(0);

    await page.goto("/admin/users");
    await expect(page).toHaveURL("/");
    await expect(
      page.getByRole("heading", { name: "Hello, John!" }),
    ).toBeVisible();
    await expect(page.getByRole("table")).toHaveCount(0);
  });

  test("Scenario 7: An HTML-looking first name shows up as literal text in the admin table", async ({
    page,
  }, testInfo) => {
    const firstName = "<img src=x onerror=alert(1)>";
    const user = uniqueUser(testInfo, { firstName });
    const dialogs: string[] = [];
    page.on("dialog", async (dialog) => {
      dialogs.push(dialog.message());
      await dialog.dismiss();
    });

    await registerThroughUi(page, user);
    await logInAsAdmin(page);
    await adminLink(page).click();

    const row = userRow(page, user.username);
    await expect(row.getByRole("cell", { name: firstName })).toBeVisible();
    await expect(page.locator("img")).toHaveCount(0);
    expect(dialogs).toEqual([]);
    // The CSP fixture fails the test on any violation.
  });
});
