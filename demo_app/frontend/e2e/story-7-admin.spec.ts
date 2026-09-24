import { expect, test } from "./fixtures";
import {
  ADMIN,
  ADMIN_USERS_PATH,
  JOHN,
  adminLink,
  logInAsAdmin,
  logInAsJohn,
  registerThroughUi,
  uniqueUser,
  userRow,
} from "./support";

const OWN_ACCOUNT_NOTE = "You can't change your own account.";

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
