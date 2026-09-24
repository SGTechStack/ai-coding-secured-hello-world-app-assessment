import { expect, test } from "./fixtures";
import {
  INVALID_RESET_LINK,
  PASSWORD_UPDATED_NOTICE,
  RESET_REQUESTED,
  loginForm,
  logInThroughUi,
  registerThroughUi,
  requestResetThroughUi,
  resetLinkFor,
  submitNewPassword,
  uniqueUser,
} from "./support";

// The emailed link is read from the backend's log file (see resetLinkFor in support.ts).
test.describe("Story 6: Password reset", () => {
  test("Scenario 1: A user resets their password through the emailed link and logs in with the new one", async ({
    page,
  }, testInfo) => {
    const user = uniqueUser(testInfo, { firstName: "Rosa" });
    const newPassword = `${user.password} renewed`;
    await registerThroughUi(page, user);

    await test.step("request a link from the login page", async () => {
      await page.getByRole("link", { name: "Forgot password?" }).click();
      await expect(page).toHaveURL("/forgot-password");
      await page.getByLabel("Email").fill(user.email);
      await page.getByRole("button", { name: "Send reset link" }).click();
      await expect(page.getByRole("status")).toHaveText(RESET_REQUESTED);
    });

    await test.step("follow the link: the token leaves the address bar", async () => {
      await page.goto(await resetLinkFor(user.email));
      await expect(
        page.getByRole("heading", { name: "Set a new password" }),
      ).toBeVisible();
      await expect(page).toHaveURL("/reset-password");
    });

    await test.step("set the new password and land on /login", async () => {
      await submitNewPassword(page, newPassword);
      await expect(page).toHaveURL("/login");
      await expect(page.getByRole("status")).toHaveText(
        PASSWORD_UPDATED_NOTICE,
      );
    });

    await test.step("log in with the new password", async () => {
      const form = loginForm(page);
      await form.username.fill(user.username);
      await form.password.fill(newPassword);
      await form.submit.click();
      await expect(page).toHaveURL("/");
      await expect(page.getByRole("heading", { level: 1 })).toHaveText(
        "Hello, Rosa!",
      );
    });
  });

  test("Scenario 2: A request for an unregistered email shows the same generic message", async ({
    page,
  }, testInfo) => {
    const nobody = uniqueUser(testInfo);

    await requestResetThroughUi(page, nobody.email);

    await expect(page).toHaveURL("/forgot-password");
  });

  test("Scenario 3: Reusing a link that has already been used shows the invalid-link message", async ({
    page,
  }, testInfo) => {
    const user = uniqueUser(testInfo);
    await registerThroughUi(page, user);
    await requestResetThroughUi(page, user.email);
    const link = await resetLinkFor(user.email);

    await page.goto(link);
    await submitNewPassword(page, `${user.password} first`);
    await expect(page).toHaveURL("/login");

    await test.step("the same link again", async () => {
      await page.goto(link);
      await submitNewPassword(page, `${user.password} second`);
      await expect(page.getByRole("alert")).toHaveText(INVALID_RESET_LINK);
      await page
        .getByRole("link", { name: "Request a new reset link" })
        .click();
      await expect(page).toHaveURL("/forgot-password");
    });

    await test.step("the first new password still works", async () => {
      await logInThroughUi(page, {
        ...user,
        password: `${user.password} first`,
      });
    });
  });

  test("Scenario 4: A reset from another browser signs the first one out", async ({
    page,
    browser,
  }, testInfo) => {
    const user = uniqueUser(testInfo, { firstName: "Ines" });
    await registerThroughUi(page, user);
    await logInThroughUi(page, user);

    await test.step("reset the password in another browser context", async () => {
      const other = await browser.newContext();
      try {
        const otherPage = await other.newPage();
        await requestResetThroughUi(otherPage, user.email);
        await otherPage.goto(await resetLinkFor(user.email));
        await submitNewPassword(otherPage, `${user.password} elsewhere`);
        await expect(otherPage).toHaveURL("/login");
      } finally {
        await other.close();
      }
    });

    await test.step("the first context's next navigation lands on /login", async () => {
      await page.goto("/");
      await expect(page).toHaveURL("/login");
      await expect(page.getByRole("heading", { name: "Log in" })).toBeVisible();
    });
  });
});
