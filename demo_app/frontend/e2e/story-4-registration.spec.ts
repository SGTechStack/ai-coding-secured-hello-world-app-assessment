import type { Page } from "@playwright/test";
import { expect, test } from "./fixtures";
import {
  JOHN,
  REGISTERED_NOTICE,
  REGISTER_PATH,
  loginForm,
  logInThroughUi,
  registerThroughUi,
  registrationForm,
  submitRegistration,
  uniqueUser,
} from "./support";

/** Every registration request the page sends, in order. */
function recordRegistrations(page: Page): string[] {
  const sent: string[] = [];
  page.on("request", (request) => {
    if (new URL(request.url()).pathname === REGISTER_PATH) {
      sent.push(request.method());
    }
  });
  return sent;
}

test.describe("Story 4: Registration", () => {
  test("Scenario 1: A visitor registers, lands on /login with the notice, logs in and is greeted", async ({
    page,
  }, testInfo) => {
    const user = uniqueUser(testInfo, { firstName: "Grace" });

    await test.step("find registration from the login page", async () => {
      await page.goto("/login");
      await page.getByRole("link", { name: "Create an account" }).click();
      await expect(page).toHaveURL("/register");
    });

    await test.step("register", async () => {
      await submitRegistration(page, user);
      await expect(page).toHaveURL("/login");
      await expect(page.getByRole("status")).toHaveText(REGISTERED_NOTICE);
    });

    await test.step("log in and see the greeting", async () => {
      const form = loginForm(page);
      await form.username.fill(user.username);
      await form.password.fill(user.password);
      await form.submit.click();
      await expect(page).toHaveURL("/");
      await expect(page.getByRole("heading", { level: 1 })).toHaveText(
        "Hello, Grace!",
      );
    });
  });

  test("Scenario 2: Registering the taken username johndoe shows a username error and creates no account", async ({
    page,
  }, testInfo) => {
    const taken = uniqueUser(testInfo, { username: JOHN.username });

    await page.goto("/register");
    await submitRegistration(page, taken);

    const username = registrationForm(page).username;
    await expect(username).toHaveAttribute("aria-invalid", "true");
    await expect(username).toHaveAccessibleDescription(
      "This username is already taken.",
    );
    await expect(username).toBeFocused();
    await expect(page).toHaveURL("/register");

    await test.step("the refused request took nothing: its email is still free", async () => {
      await registerThroughUi(
        page,
        uniqueUser(testInfo, { email: taken.email }),
      );
    });
  });

  test("Scenario 3: An 8-character password shows the password policy error", async ({
    page,
  }, testInfo) => {
    const sent = recordRegistrations(page);

    await page.goto("/register");
    await submitRegistration(
      page,
      uniqueUser(testInfo, { password: "Short1!x" }),
    );

    const password = registrationForm(page).password;
    await expect(password).toHaveAttribute("aria-invalid", "true");
    await expect(password).toHaveAccessibleDescription(
      "Password must be 12 to 64 characters.",
    );
    await expect(page).toHaveURL("/register");
    expect(sent).toEqual([]);
  });

  test("Scenario 4: An HTML first name is shown as literal text, runs nothing and breaks no CSP", async ({
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
    await logInThroughUi(page, user);

    await expect(page.getByRole("heading", { level: 1 })).toHaveText(
      `Hello, ${firstName}!`,
    );
    await expect(page.locator("img")).toHaveCount(0);
    expect(dialogs).toEqual([]);
    // The CSP fixture fails the test on any violation.
  });
});
